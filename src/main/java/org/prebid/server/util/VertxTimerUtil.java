package org.prebid.server.util;

import io.vertx.core.Vertx;
import org.prebid.server.vertx.Initializable;

import java.util.concurrent.ThreadLocalRandom;

public class VertxTimerUtil {

    private VertxTimerUtil() {
    }

    public static void setTimerWithJitter(Vertx vertx, Initializable task, long delay, long jitter) {
        final long nextDelay = delay + ThreadLocalRandom.current().nextLong(jitter * -1, jitter);
        vertx.setTimer(delay, parameter -> {
            task.initialize();
            setTimerWithJitter(vertx, task, nextDelay, jitter);
        });
    }
}
