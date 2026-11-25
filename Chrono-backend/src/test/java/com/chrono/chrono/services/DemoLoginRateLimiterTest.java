package com.chrono.chrono.services;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoLoginRateLimiterTest {

    @Test
    void checkBlocksAfterConfiguredIpLimit() {
        DemoLoginRateLimiter limiter = new DemoLoginRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxRequestsPerIp", 2);
        ReflectionTestUtils.setField(limiter, "globalMaxRequests", 100);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 3600L);

        assertTrue(limiter.check("127.0.0.1").allowed());
        assertTrue(limiter.check("127.0.0.1").allowed());
        DemoLoginRateLimiter.RateLimitDecision limited = limiter.check("127.0.0.1");

        assertFalse(limited.allowed());
        assertTrue(limited.retryAfterSeconds() > 0);
    }
}
