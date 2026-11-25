package com.chrono.chrono.config;

import com.chrono.chrono.entities.pms.PmsAuditEvent;
import com.chrono.chrono.entities.pms.PublicBookingRequest;
import com.chrono.chrono.entities.pms.PublicBookingVerification;
import com.chrono.chrono.entities.pms.PublicRateLimit;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PmsProductionConfigurationTest {

    @Test
    void liveProfileValidatesSchemaAndKeepsLegacyReadPathsCompatible() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application-live.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }

        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.jpa.open-in-view")).isEqualTo("true");
        assertThat(properties.getProperty("app.production")).isEqualTo("true");
        assertThat(properties.getProperty("app.security.allowed-origins"))
                .isEqualTo("${APP_SECURITY_ALLOWED_ORIGINS}");
        assertThat(properties.getProperty("app.demo-login.enabled"))
                .isEqualTo("${APP_DEMO_LOGIN_ENABLED:false}");
        assertThat(properties.getProperty("management.server.port")).isEqualTo("8082");
    }

    @Test
    void reliabilityMigrationContainsAuditAndRetrySchema() throws IOException {
        String migration;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V14__pms_audit_and_outbox_reliability.sql")) {
            assertThat(input).isNotNull();
            migration = new String(input.readAllBytes());
        }

        assertThat(migration)
                .contains("CREATE TABLE pms_audit_events")
                .contains("integrity_hash")
                .contains("next_attempt_at")
                .contains("last_error")
                .contains("lock_owner");
    }

    @Test
    void auditChainPreviousHashMatchesProductionColumnType() throws NoSuchFieldException {
        Field previousHash = PmsAuditEvent.class.getDeclaredField("previousHash");
        Column column = previousHash.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.columnDefinition()).isEqualTo("char(64)");
    }

    @Test
    void publicBookingFingerprintMatchesProductionColumnType() throws NoSuchFieldException {
        assertChar64Column(PublicBookingRequest.class, "requestFingerprint");
    }

    @Test
    void publicRateLimitKeyMatchesProductionColumnType() throws NoSuchFieldException {
        assertChar64Column(PublicRateLimit.class, "rateKey");
    }

    @Test
    void publicBookingVerificationTokenMatchesProductionColumnType() throws NoSuchFieldException {
        assertChar64Column(PublicBookingVerification.class, "tokenHash");
    }

    private void assertChar64Column(Class<?> entityType, String fieldName) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.columnDefinition()).isEqualTo("char(64)");
    }
}
