package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
        String compose = Files.readString(REPOSITORY_ROOT.resolve("docker-compose.production.yml"));

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
    void productionDeployCanOnlyReplaceApplicationContainers() throws IOException {
        String compose = Files.readString(REPOSITORY_ROOT.resolve("docker-compose.production.yml"));
        String deploy = Files.readString(REPOSITORY_ROOT.resolve("ops/deploy-production.sh"));
        String update = Files.readString(REPOSITORY_ROOT.resolve("update.sh"));
        String desktopDeploy = Files.readString(REPOSITORY_ROOT.resolve("chrono-deploy.bat"));

        assertThat(compose)
                .contains("name: chrono")
                .contains("\n  backend:\n")
                .contains("\n  frontend:\n")
                .contains("external: true")
                .contains("name: chrono_chrono")
                .contains("aliases: [backend]")
                .contains("aliases: [frontend]")
                .contains("http://127.0.0.1:80/healthz")
                .contains("net.ipv4.ip_unprivileged_port_start: \"0\"")
                .doesNotContain("\n  mysql:\n")
                .doesNotContain("\n  nginx:\n")
                .doesNotContain("\n  prometheus:\n")
                .doesNotContain("\n  grafana:\n")
                .doesNotContain("\nvolumes:\n");

        assertThat(update)
                .contains("git pull --ff-only")
                .contains("CHRONO_UPDATE_REEXECUTED")
                .contains("exec bash ./update.sh")
                .contains("exec bash ./ops/deploy-production.sh");
        assertThat(deploy)
                .contains("docker-compose.production.yml")
                .contains("config --services")
                .contains("\"backend frontend\"")
                .contains("mysqldump")
                .contains("gzip -t")
                .contains("sha256sum")
                .contains("snapshot_protected_containers")
                .contains("assert_protected_containers_unchanged")
                .contains("assert_core_counts_not_decreased")
                .contains("up -d --no-deps --wait --wait-timeout 180 backend frontend")
                .contains("MySQL-Volume unverändert")
                .doesNotContain("--remove-orphans")
                .doesNotContain("docker compose down")
                .doesNotContain("docker volume prune")
                .doesNotContain("docker volume rm")
                .doesNotContain("docker container prune")
                .doesNotContain("docker system prune")
                .doesNotContain("docker network rm");
        assertThat(desktopDeploy)
                .contains("git rev-parse --short^=12 HEAD")
                .contains("REMOTE_UPDATE_CMD=cd ~/chrono && git pull --ff-only "
                        + "&& bash ./ops/deploy-production.sh --image-tag !IMAGE_TAG!")
                .contains("%FE_REPO%:!IMAGE_TAG!")
                .contains("%BE_REPO%:!IMAGE_TAG!")
                .doesNotContain(":latest")
                .doesNotContain("REMOTE_CLEANUP_CMD")
                .doesNotContain("docker container prune")
                .doesNotContain("docker image prune -a")
                .doesNotContain("--remove-orphans");
    }

    @Test
    void infrastructureComposeIsExplicitAndCannotOwnSharedNetwork() throws IOException {
        String compose = Files.readString(REPOSITORY_ROOT.resolve("docker-compose.yml"));
        String gitignore = Files.readString(REPOSITORY_ROOT.resolve(".gitignore"));

        assertThat(compose)
                .contains("\"127.0.0.1:9090:9090\"")
                .contains("\"127.0.0.1:9093:9093\"")
                .contains("\"127.0.0.1:3000:3000\"")
                .contains("nginx:")
                .contains("NGINX_PROXY_MANAGER_IMAGE")
                .contains("./data:/data")
                .contains("./letsencrypt:/etc/letsencrypt")
                .contains("networks: [chrono]")
                .contains("profiles: [\"infrastructure\"]")
                .contains("external: true")
                .contains("name: chrono_chrono")
                .contains("open-webui:")
                .contains("OLLAMA_IMAGE")
                .contains("ALERT_EMAIL_TO")
                .contains("mysql-backup:")
                .contains("backup-restore-test:")
                .contains("profiles: [\"restore-test\"]")
                .doesNotContain("\"3307:3306\"");
        for (String service : new String[]{
                "mysql",
                "backend",
                "frontend",
                "nginx",
                "mysql-backup",
                "prometheus",
                "alertmanager",
                "grafana",
                "llm",
                "model-puller",
                "open-webui"
        }) {
            assertThat(compose)
                    .as("infrastructure service %s must require an explicit profile", service)
                    .contains("  " + service + ":\n    profiles: [\"infrastructure\"]");
        }
        assertThat(REPOSITORY_ROOT.resolve("ops/backup/backup.sh")).isRegularFile();
        assertThat(gitignore).contains("!ops/backup/backup.sh");
    }

    @Test
    void migrationsDoNotContainDestructiveTableOrRowOperations() throws IOException {
        Path migrationDirectory = REPOSITORY_ROOT.resolve(
                "Chrono-backend/src/main/resources/db/migration");
        Pattern destructive = Pattern.compile(
                "\\bDROP\\s+(DATABASE|TABLE|COLUMN)\\b"
                        + "|\\bTRUNCATE\\s+TABLE\\b"
                        + "|\\bDELETE\\s+FROM\\b"
                        + "|\\bRENAME\\s+TABLE\\b",
                Pattern.CASE_INSENSITIVE);

        try (Stream<Path> files = Files.list(migrationDirectory)) {
            files.filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".sql"))
                    .forEach(path -> {
                        try {
                            assertThat(destructive.matcher(Files.readString(path)).find())
                                    .as("destructive migration operation in %s", path.getFileName())
                                    .isFalse();
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
    }

    @Test
    void containerBuildsRunTestsAndUseNonRootRuntimes() throws IOException {
        String backend = Files.readString(REPOSITORY_ROOT.resolve("Chrono-backend/Dockerfile"));
        String frontend = Files.readString(REPOSITORY_ROOT.resolve("Chrono-frontend/Dockerfile"));
        String frontendNginx = Files.readString(REPOSITORY_ROOT.resolve("Chrono-frontend/nginx.conf"));
        String application = Files.readString(
                REPOSITORY_ROOT.resolve("Chrono-backend/src/main/java/com/chrono/chrono/ChronoApplication.java"));
        String liveProperties = Files.readString(
                REPOSITORY_ROOT.resolve("Chrono-backend/src/main/resources/application-live.properties"));
        String logback = Files.readString(
                REPOSITORY_ROOT.resolve("Chrono-backend/src/main/resources/logback-spring.xml"));

        assertThat(backend)
                .contains("mvn -B clean verify")
                .contains("distroless/java21-debian12:nonroot")
                .contains("EXPOSE 8081")
                .doesNotContain("maven.test.skip");
        assertThat(frontend)
                .contains("npm ci --legacy-peer-deps")
                .contains("RUN npm test")
                .contains("nginx-unprivileged")
                .contains("EXPOSE 80");
        assertThat(frontendNginx)
                .contains("listen 80 default_server")
                .contains("location = /healthz");
        assertThat(application)
                .doesNotContain("Datasource URL")
                .doesNotContain("System.out");
        assertThat(liveProperties)
                .contains("llm.warmup.enabled=${LLM_WARMUP_ENABLED:false}")
                .contains("logging.level.org.hibernate.SQL=${HIBERNATE_SQL_LOG_LEVEL:INFO}");
        assertThat(logback).doesNotContain("level=\"DEBUG\"");
    }
}
