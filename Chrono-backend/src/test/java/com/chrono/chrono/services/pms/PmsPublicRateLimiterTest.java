package com.chrono.chrono.services.pms;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class PmsPublicRateLimiterTest {

    @Test
    void limitsEachIpAndOperationIndependently() {
        PmsPublicRateLimiter limiter = new PmsPublicRateLimiter(2, 60);

        assertThat(limiter.check("127.0.0.1", "read").allowed()).isTrue();
        assertThat(limiter.check("127.0.0.1", "read").allowed()).isTrue();
        PmsPublicRateLimiter.Decision denied = limiter.check("127.0.0.1", "read");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isPositive();

        assertThat(limiter.check("127.0.0.1", "complete").allowed()).isTrue();
        assertThat(limiter.check("127.0.0.2", "read").allowed()).isTrue();
    }

    @Test
    void remainsExactUnderConcurrentWebhookLoad() throws Exception {
        int requestLimit = 25;
        PmsPublicRateLimiter limiter = new PmsPublicRateLimiter(requestLimit, 60);
        var executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> requests = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                requests.add(() -> limiter.check(
                        "203.0.113.10", "channel-webhook:CHANNEL_TEST").allowed());
            }

            long accepted = executor.invokeAll(requests).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .count();

            assertThat(accepted).isEqualTo(requestLimit);
        } finally {
            executor.shutdownNow();
        }
    }
}
