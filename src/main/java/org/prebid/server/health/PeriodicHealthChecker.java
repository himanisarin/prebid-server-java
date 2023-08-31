package org.prebid.server.health;

import io.vertx.core.Vertx;
import org.prebid.server.util.VertxTimerUtil;

import java.util.Objects;

public abstract class PeriodicHealthChecker implements HealthChecker {

    private final Vertx vertx;
    private final long refreshPeriod;
    private final long jitter;

    PeriodicHealthChecker(Vertx vertx, long refreshPeriod, long jitter) {
        this.vertx = Objects.requireNonNull(vertx);
        this.refreshPeriod = verifyRefreshPeriod(refreshPeriod);
        this.jitter = verifyRefreshPeriodJitter(refreshPeriod, jitter);
    }

    public void initialize() {
        updateStatus();
        VertxTimerUtil.setTimerWithJitter(vertx, this::updateStatus, refreshPeriod, jitter);
    }

    abstract void updateStatus();

    private static long verifyRefreshPeriod(long refreshPeriod) {
        if (refreshPeriod < 1) {
            throw new IllegalArgumentException("Refresh period for updating status be positive value");
        }
        return refreshPeriod;
    }

    private static long verifyRefreshPeriodJitter(long refreshPeriod, long jitter) {
        if (jitter < 1 || jitter > refreshPeriod) {
            throw new IllegalArgumentException(
                    "Refresh period jitter for updating status be positive value and less than refresh period");
        }
        return jitter;
    }
}
