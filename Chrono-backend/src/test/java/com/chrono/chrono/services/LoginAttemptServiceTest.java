package com.chrono.chrono.services;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Deque;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    @Test
    void blocksAfterTooManyFailures() {
        LoginAttemptService service = new LoginAttemptService();

        for (int i = 0; i < 10; i++) {
            service.recordFailure("john@127.0.0.1");
        }

        assertTrue(service.isBlocked("john@127.0.0.1"));
    }

    @Test
    void clearsBlockStateAfterSuccessfulLogin() {
        LoginAttemptService service = new LoginAttemptService();

        for (int i = 0; i < 10; i++) {
            service.recordFailure("john@127.0.0.1");
        }

        service.recordSuccess("john@127.0.0.1");

        assertFalse(service.isBlocked("john@127.0.0.1"));
    }

    @Test
    void rotatingAccountKeysCannotGrowTheCacheOrEvictAnActiveBlock() {
        LoginAttemptService service = new LoginAttemptService(Clock.systemUTC(), 2, 2, 3, 5);
        for (int i = 0; i < 10; i++) service.recordFailure("blocked@ip");
        service.recordFailure("second@ip");
        for (int i = 0; i < 100; i++) service.recordFailure("rotating-" + i + "@ip");

        assertEquals(2, accountState(service).size());
        assertTrue(service.isBlocked("blocked@ip"));
        assertTrue(service.isBlocked("untracked@ip"));
        service.recordSuccess("second@ip");
        assertFalse(service.isBlocked("untracked@ip"));
    }

    @Test
    void repeatedFailuresForOneKeyKeepOnlyTheBoundedFailureWindow() {
        LoginAttemptService service = new LoginAttemptService();
        for (int i = 0; i < 10_000; i++) service.recordFailure("same@ip");
        Object window = accountState(service).get("same@ip");

        assertEquals(10, ((Deque<?>) ReflectionTestUtils.getField(window, "failures")).size());
        assertTrue(service.isBlocked("same@ip"));
    }

    @Test
    void scheduledCleanupReclaimsExpiredAccountsAndIpsWithoutRevisitingTheirKeys() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(clock, 2, 2, 3, 5);
        service.recordFailure("one-off@ip");
        for (int i = 0; i < 10; i++) service.recordFailure("blocked@ip");
        assertFalse(service.isRequestRateLimited("one-off-ip"));
        clock.advance(Duration.ofMinutes(15));

        service.removeExpiredAttempts();

        assertTrue(accountState(service).isEmpty());
        assertTrue(ipState(service).isEmpty());
        assertTrue(((Deque<?>) ReflectionTestUtils.getField(service, "globalRequests")).isEmpty());
        assertFalse(service.isBlocked("new-key@ip"));
    }

    @Test
    void blocksBeforeWindowExpiresAndAllowsLoginAfterExpiry() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(clock, 2, 2, 3, 5);
        for (int i = 0; i < 10; i++) service.recordFailure("john@ip");
        clock.advance(Duration.ofMinutes(14));
        assertTrue(service.isBlocked("john@ip"));
        clock.advance(Duration.ofMinutes(1));
        assertFalse(service.isBlocked("john@ip"));
    }

    @Test
    void perIpAndGlobalLimitsApplyAcrossDifferentUsernamesAndHaveBoundedState() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(clock, 10, 2, 2, 3);
        assertFalse(service.isRequestRateLimited("ip-one"));
        assertFalse(service.isRequestRateLimited("ip-one"));
        assertTrue(service.isRequestRateLimited("ip-one"));
        assertFalse(service.isRequestRateLimited("ip-two"));
        for (int i = 0; i < 100; i++) assertTrue(service.isRequestRateLimited("rotating-ip-" + i));

        assertEquals(2, ipState(service).size());
        assertEquals(3, ((Deque<?>) ReflectionTestUtils.getField(service, "globalRequests")).size());
        assertTrue(accountState(service).isEmpty());
        clock.advance(Duration.ofMinutes(1));
        assertFalse(service.isRequestRateLimited("new-ip"));
        assertEquals(1, ipState(service).size());
    }

    @Test
    void ipCapacityIsBoundedEvenBeforeTheGlobalLimitIsReached() {
        LoginAttemptService service = new LoginAttemptService(Clock.systemUTC(), 10, 2, 10, 100);
        assertFalse(service.isRequestRateLimited("ip-one"));
        assertFalse(service.isRequestRateLimited("ip-two"));
        for (int i = 0; i < 100; i++) assertTrue(service.isRequestRateLimited("rotating-ip-" + i));

        assertEquals(2, ipState(service).size());
        assertFalse(service.isRequestRateLimited("ip-one"));
    }

    @Test
    void oversizedKeysCannotEnterEitherMap() {
        LoginAttemptService service = new LoginAttemptService();
        String oversized = "x".repeat(2_000);
        service.recordFailure(oversized);
        assertTrue(service.isBlocked(oversized));
        assertFalse(service.isRequestRateLimited(oversized));

        assertTrue(accountState(service).isEmpty());
        assertEquals(1, ipState(service).size());
        assertTrue(ipState(service).containsKey("unknown"));
    }

    private Map<?, ?> accountState(LoginAttemptService service) {
        return (Map<?, ?>) ReflectionTestUtils.getField(service, "attempts");
    }

    private Map<?, ?> ipState(LoginAttemptService service) {
        return (Map<?, ?>) ReflectionTestUtils.getField(service, "requestsByIp");
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-07T12:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
