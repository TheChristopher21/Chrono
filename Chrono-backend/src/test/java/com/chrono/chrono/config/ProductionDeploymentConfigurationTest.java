package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionDeploymentConfigurationTest {

    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();

    @BeforeAll
    static void requiresFullRepositoryContext() {
        Assumptions.assumeTrue(
                Files.isRegularFile(REPOSITORY_ROOT.resolve("docker-compose.yml")),
                "Repository topology checks require the full repository, not the isolated backend image context");
    }

    @Test
    void composeDisablesDemoDataAndUsesDedicatedDatabaseAccount() throws IOException {
        String compose = Files.readString(REPOSITORY_ROOT.resolve("docker-compose.yml"));

        assertThat(compose)
                .contains("APP_DEMO_LOGIN_ENABLED: \"false\"")
                .contains("APP_DEMO_LOGIN_ALLOW_PRODUCTION: \"false\"")
                .contains("APP_PMS_TEST_ACCOUNT_ENABLED: \"false\"")
                .contains("APP_PMS_DEMO_DATA_ENABLED: \"false\"")
                .contains("SPRING_DATASOURCE_USERNAME: \"${MYSQL_USER:?MYSQL_USER must be set}\"")
                .doesNotContain("SPRING_DATASOURCE_USERNAME: \"root\"")
                .doesNotContain(":latest");
    }

    @Test
    void composeKeepsMonitoringPortsOnLoopbackAndHasRestoreDrill() throws IOException {
        String compose = Files.readString(REPOSITORY_ROOT.resolve("docker-compose.yml"));

        assertThat(compose)
                .contains("\"127.0.0.1:9090:9090\"")
                .contains("\"127.0.0.1:3000:3000\"")
                .contains("gateway:")
                .contains("./ops/gateway/Caddyfile:/etc/caddy/Caddyfile:ro")
                .contains("mysql-backup:")
                .contains("backup-restore-test:")
                .contains("profiles: [\"restore-test\"]")
                .doesNotContain("\"3307:3306\"");
    }

    @Test
    void containerBuildsRunTestsAndUseNonRootRuntimes() throws IOException {
        String backend = Files.readString(REPOSITORY_ROOT.resolve("Chrono-backend/Dockerfile"));
        String frontend = Files.readString(REPOSITORY_ROOT.resolve("Chrono-frontend/Dockerfile"));
        String application = Files.readString(
                REPOSITORY_ROOT.resolve("Chrono-backend/src/main/java/com/chrono/chrono/ChronoApplication.java"));
        String liveProperties = Files.readString(
                REPOSITORY_ROOT.resolve("Chrono-backend/src/main/resources/application-live.properties"));
        String logback = Files.readString(
                REPOSITORY_ROOT.resolve("Chrono-backend/src/main/resources/logback-spring.xml"));

        assertThat(backend)
                .contains("mvn -B clean verify")
                .contains("distroless/java21-debian12:nonroot")
                .doesNotContain("maven.test.skip");
        assertThat(frontend)
                .contains("npm ci --legacy-peer-deps")
                .contains("RUN npm test")
                .contains("nginx-unprivileged");
        assertThat(application)
                .doesNotContain("Datasource URL")
                .doesNotContain("System.out");
        assertThat(liveProperties)
                .contains("llm.warmup.enabled=${LLM_WARMUP_ENABLED:false}")
                .contains("logging.level.org.hibernate.SQL=${HIBERNATE_SQL_LOG_LEVEL:INFO}");
        assertThat(logback).doesNotContain("level=\"DEBUG\"");
    }
}
