package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityStartupValidatorTest {

    @Test
    void run_rejectsProductionDemoLoginWithoutExplicitAllowFlag() {
        SecurityStartupValidator validator = productionValidator();
        ReflectionTestUtils.setField(validator, "demoLoginEnabled", true);
        ReflectionTestUtils.setField(validator, "demoLoginAllowedInProduction", false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));

        org.junit.jupiter.api.Assertions.assertEquals(
                "Production demo login requires APP_DEMO_LOGIN_ALLOW_PRODUCTION=true",
                ex.getMessage()
        );
    }

    @Test
    void run_allowsProductionDemoLoginWhenExplicitlyAllowed() {
        SecurityStartupValidator validator = productionValidator();
        ReflectionTestUtils.setField(validator, "demoLoginEnabled", true);
        ReflectionTestUtils.setField(validator, "demoLoginAllowedInProduction", true);

        assertDoesNotThrow(() -> validator.run(null));
    }

    private SecurityStartupValidator productionValidator() {
        SecurityStartupValidator validator = new SecurityStartupValidator();
        ReflectionTestUtils.setField(validator, "production", true);
        ReflectionTestUtils.setField(validator, "initializeAdmin", false);
        ReflectionTestUtils.setField(validator, "adminPassword", "");
        ReflectionTestUtils.setField(validator, "jwtSecret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(validator, "nfcAgentToken", "abcdef0123456789abcdef0123456789");
        ReflectionTestUtils.setField(validator, "allowLocalhostLegacy", false);
        ReflectionTestUtils.setField(validator, "publicIcsFeedWithoutToken", false);
        ReflectionTestUtils.setField(validator, "reportIcsFeedToken", "feedtoken0123456789abcdef01234567");
        return validator;
    }
}
