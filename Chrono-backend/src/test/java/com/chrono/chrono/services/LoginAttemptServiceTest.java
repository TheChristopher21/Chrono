package com.chrono.chrono.services;

import org.junit.jupiter.api.Test;

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
}
