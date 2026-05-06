package com.chrono.chrono.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportControllerTest {

    private ReportController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportController();
        ReflectionTestUtils.setField(controller, "icsFeedToken", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(controller, "publicIcsFeedWithoutToken", false);
    }

    @Test
    void feedTokenIsBoundToUsername() {
        String aliceToken = controller.feedTokenForUsername("alice");

        assertNotNull(aliceToken);
        assertTrue(controller.validFeedToken("alice", aliceToken));
        assertFalse(controller.validFeedToken("bob", aliceToken));
        assertFalse(controller.validFeedToken("alice", "0123456789abcdef0123456789abcdef"));
    }
}
