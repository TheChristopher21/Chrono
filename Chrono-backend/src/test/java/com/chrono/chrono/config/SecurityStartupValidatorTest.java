package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityStartupValidatorTest {

    @Test
    void run_rejectsProductionDemoLogin() {
        SecurityStartupValidator validator = productionValidator();
        ReflectionTestUtils.setField(validator, "demoLoginEnabled", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));

        assertEquals("Demo login must be disabled in production", ex.getMessage());
    }

    @Test
    void run_doesNotAllowProductionOverrideForDemoLogin() {
        SecurityStartupValidator validator = productionValidator();
        ReflectionTestUtils.setField(validator, "demoLoginAllowedInProduction", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));

        assertEquals("Demo login must be disabled in production", ex.getMessage());
    }

    @Test
    void run_rejectsPmsTestDataInProduction() {
        SecurityStartupValidator validator = productionValidator();
        ReflectionTestUtils.setField(validator, "pmsTestAccountEnabled", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));

        assertEquals("PMS test accounts and demo data must be disabled in production", ex.getMessage());
    }

    @Test
    void run_rejectsLocalhostCorsInProduction() {
        SecurityStartupValidator validator = productionValidator();
        ReflectionTestUtils.setField(
                validator,
                "allowedOrigins",
                "https://chrono-logisch.ch,http://localhost:5173");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));

        assertEquals("Production CORS origins must use explicit public HTTPS URLs", ex.getMessage());
    }

    @Test
    void run_rejectsSimulatedPaymentsInProduction() {
        SecurityStartupValidator validator = productionValidator();
        ReflectionTestUtils.setField(validator, "simulatedPmsPaymentsEnabled", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));

        assertEquals("Simulated PMS payments must be disabled in production", ex.getMessage());
    }

    @Test
    void run_rejectsLongPlaceholderSecretsInProduction() {
        SecurityStartupValidator validator = productionValidator();
        ReflectionTestUtils.setField(
                validator,
                "jwtSecret",
                "replace-with-a-random-secret-that-is-long-enough");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validator.run(null));

        assertEquals(
                "JWT_SECRET must be set to a non-default secret of at least 32 characters",
                ex.getMessage());
    }

    private SecurityStartupValidator productionValidator() {
        SecurityStartupValidator validator = new SecurityStartupValidator();
        ReflectionTestUtils.setField(validator, "production", true);
        ReflectionTestUtils.setField(validator, "demoLoginEnabled", false);
        ReflectionTestUtils.setField(validator, "demoLoginAllowedInProduction", false);
        ReflectionTestUtils.setField(validator, "pmsTestAccountEnabled", false);
        ReflectionTestUtils.setField(validator, "pmsDemoDataEnabled", false);
        ReflectionTestUtils.setField(validator, "simulatedPmsPaymentsEnabled", false);
        ReflectionTestUtils.setField(
                validator,
                "allowedOrigins",
                "https://chrono-logisch.ch,https://www.chrono-logisch.ch");
        ReflectionTestUtils.setField(validator, "initializeAdmin", false);
        ReflectionTestUtils.setField(validator, "adminPassword", "");
        ReflectionTestUtils.setField(validator, "jwtSecret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(validator, "nfcAgentToken", "abcdef0123456789abcdef0123456789");
        ReflectionTestUtils.setField(validator, "allowLocalhostLegacy", false);
        ReflectionTestUtils.setField(validator, "publicIcsFeedWithoutToken", false);
        ReflectionTestUtils.setField(validator, "reportIcsFeedToken", "feedtoken0123456789abcdef01234567");
        ReflectionTestUtils.setField(validator, "pmsDocumentHmacKey", "document0123456789abcdef0123456789");
        ReflectionTestUtils.setField(validator, "pmsAuditHmacKey", "auditkey0123456789abcdef01234567890");
        return validator;
    }
}
