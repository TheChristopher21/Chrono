package com.chrono.chrono.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class PmsFlywayBaselineTest {

    @Test
    void createsTheCurrentChronoAndPmsSchemaOnAnEmptyDatabase() throws Exception {
        String url = "jdbc:h2:mem:chrono_flyway_baseline;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        var result = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(result.migrationsExecuted).isEqualTo(2);

        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "pms_properties")).isTrue();
            assertThat(tableExists(connection, "pms_reservations")).isTrue();
            assertThat(tableExists(connection, "pms_integration_outbox")).isTrue();
            assertThat(tableExists(connection, "pms_audit_events")).isTrue();
            assertThat(columnExists(connection, "pms_integration_outbox", "next_attempt_at")).isTrue();
            assertThat(columnExists(connection, "pms_integration_outbox", "lock_owner")).isTrue();
        }
    }

    @Test
    void baselinesAnExistingDatabaseAndAddsOnlyTheMissingPmsSchema() throws Exception {
        String url = "jdbc:h2:mem:chrono_flyway_existing;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("create table companies (id bigint primary key)");
            statement.execute("create table legacy_marker (id bigint primary key, marker_value varchar(32))");
            statement.execute("insert into legacy_marker (id, marker_value) values (1, 'preserved')");
        }

        var result = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("14")
                .load()
                .migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            assertThat(tableExists(connection, "pms_properties")).isTrue();
            assertThat(tableExists(connection, "pms_reservations")).isTrue();
            assertThat(tableExists(connection, "pms_integration_outbox")).isTrue();
            assertThat(tableExists(connection, "pms_audit_events")).isTrue();
            assertThat(columnExists(connection, "pms_integration_outbox", "next_attempt_at")).isTrue();
            assertThat(columnExists(connection, "pms_integration_outbox", "lock_owner")).isTrue();

            try (var marker = statement.executeQuery("select marker_value from legacy_marker where id = 1")) {
                assertThat(marker.next()).isTrue();
                assertThat(marker.getString("marker_value")).isEqualTo("preserved");
            }
            try (var history = statement.executeQuery(
                    "select version, type from flyway_schema_history "
                            + "where success = true and type = 'BASELINE'")) {
                assertThat(history.next()).isTrue();
                assertThat(history.getString("version")).isEqualTo("14");
                assertThat(history.getString("type")).isEqualTo("BASELINE");
            }
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws Exception {
        try (var result = connection.getMetaData().getTables(
                null, null, tableName, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private boolean columnExists(
            java.sql.Connection connection,
            String tableName,
            String columnName
    ) throws Exception {
        try (var result = connection.getMetaData().getColumns(
                null, null, tableName, columnName)) {
            return result.next();
        }
    }
}
