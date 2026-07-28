package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PmsProductionConfigurationTest {

    @Test
    void liveProfileValidatesSchemaAndDoesNotMutateIt() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application-live.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }

        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(properties.getProperty("app.production")).isEqualTo("true");
        assertThat(properties.getProperty("app.security.allowed-origins"))
                .isEqualTo("${APP_SECURITY_ALLOWED_ORIGINS}");
        assertThat(properties.getProperty("app.demo-login.enabled"))
                .isEqualTo("${APP_DEMO_LOGIN_ENABLED:false}");
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
}
