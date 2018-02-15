package org.rtb.vexing.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.execution.GlobalTimeout;

import java.time.Clock;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

public class JdbcClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private JDBCClient vertxJdbcClient;

    private JdbcClient jdbcClient;

    @Before
    public void setUp() {
        jdbcClient = new JdbcClient(vertx, vertxJdbcClient);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new JdbcClient(null, null));
        assertThatNullPointerException().isThrownBy(() -> new JdbcClient(vertx, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void initializeShouldReturnEmptySucceededFutureIfConnectionCouldBeEstablished() {
        // given
        givenGetConnectionReturning(Future.succeededFuture());

        // when
        final Future<Void> future = jdbcClient.initialize();

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNull();
    }

    @Test
    public void initializeShouldReturnFailedFutureIfConnectionCouldNotBeEstablished() {
        // given
        givenGetConnectionReturning(Future.failedFuture(new RuntimeException("Failed to open connection")));

        // when
        final Future<Void> future = jdbcClient.initialize();

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to open connection");
    }

    @Test
    public void executeQueryShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> jdbcClient.executeQuery(null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> jdbcClient.executeQuery("query", null, null, null));
        assertThatNullPointerException().isThrownBy(() -> jdbcClient.executeQuery("query", emptyList(), null, null));
        assertThatNullPointerException().isThrownBy(
                () -> jdbcClient.executeQuery("query", emptyList(), identity(), null));
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfGlobalTimeoutAlreadyExpired() {
        // when
        final Future<ResultSet> future = jdbcClient.executeQuery("query", emptyList(), identity(),
                GlobalTimeout.create(Clock.systemDefaultZone().millis() - 10000L, 1000L));

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(TimeoutException.class)
                .hasMessage("Timed out while executing SQL query");
        verifyNoMoreInteractions(vertx, vertxJdbcClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void executeQueryShouldReturnFailedFutureIfItTakesLongerThanRemainingTimeout() {
        // given
        given(vertx.setTimer(anyLong(), any())).willAnswer(invocation -> {
            ((Handler<Long>) invocation.getArgument(1)).handle(123L);
            return 123L;
        });

        final SQLConnection connection = mock(SQLConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.succeededFuture(new ResultSet()));

        // when
        final Future<ResultSet> future = jdbcClient.executeQuery("query", emptyList(), identity(),
                GlobalTimeout.create(1000L));

        // then
        final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(vertx).setTimer(timeoutCaptor.capture(), any());
        assertThat(timeoutCaptor.getValue()).isCloseTo(1000L, offset(20L));

        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(TimeoutException.class)
                .hasMessage("Timed out while executing SQL query");
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfConnectionAcquisitionFails() {
        // given
        given(vertx.setTimer(anyLong(), any())).willReturn(123L);

        givenGetConnectionReturning(Future.failedFuture(new RuntimeException("Failed to acquire connection")));

        // when
        final Future<ResultSet> future = jdbcClient.executeQuery("query", emptyList(), identity(),
                GlobalTimeout.create(1000L));

        // then
        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to acquire connection");
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfQueryFails() {
        // given
        given(vertx.setTimer(anyLong(), any())).willReturn(123L);

        final SQLConnection connection = mock(SQLConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.failedFuture(new RuntimeException("Failed to execute query")));

        // when
        final Future<ResultSet> future = jdbcClient.executeQuery("query", emptyList(), identity(),
                GlobalTimeout.create(1000L));

        // then
        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to execute query");
    }

    @Test
    public void executeQueryShouldReturnSucceededFutureWithMappedQueryResult() {
        // given
        given(vertx.setTimer(anyLong(), any())).willReturn(123L);

        final SQLConnection connection = mock(SQLConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.succeededFuture(
                new ResultSet().setResults(singletonList(new JsonArray().add("value")))));

        // when
        final Future<String> future = jdbcClient.executeQuery("query", emptyList(),
                resultSet -> resultSet.getResults().get(0).getString(0),
                GlobalTimeout.create(1000L));

        // then
        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo("value");
    }

    @SuppressWarnings("unchecked")
    private void givenGetConnectionReturning(AsyncResult<SQLConnection> result) {
        given(vertxJdbcClient.getConnection(any())).willAnswer(invocation -> {
            ((Handler<AsyncResult<SQLConnection>>) invocation.getArgument(0)).handle(result);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static void givenQueryReturning(SQLConnection connection, AsyncResult<ResultSet> result) {
        given(connection.queryWithParams(anyString(), any(), any())).willAnswer(invocation -> {
            ((Handler<AsyncResult<ResultSet>>) invocation.getArgument(2)).handle(result);
            return null;
        });
    }
}