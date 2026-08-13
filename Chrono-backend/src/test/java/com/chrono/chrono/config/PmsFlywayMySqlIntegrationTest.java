package com.chrono.chrono.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class PmsFlywayMySqlIntegrationTest {

    @Test
    @EnabledIfSystemProperty(named = "chrono.test.mysql.url", matches = ".+")
    void supportsLegacyAndFreshIsolatedMySqlDatabases() throws Exception {
        String url = System.getProperty("chrono.test.mysql.url");
        String username = System.getProperty("chrono.test.mysql.username");
        String password = System.getProperty("chrono.test.mysql.password");

        assertThat(url)
                .as("The migration integration test may only target local MySQL")
                .matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):\\d+/.*");

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("create table companies (id bigint primary key)");
            statement.execute("create table users (id bigint primary key)");
            statement.execute("""
                    create table legacy_marker (
                        id bigint primary key,
                        marker_value varchar(32) not null
                    )
                    """);
            statement.execute("""
                    insert into legacy_marker (id, marker_value)
                    values (1, 'preserved')
                    """);
        }

        var result = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("14")
                .cleanDisabled(true)
                .load()
                .migrate();

        assertThat(result.migrationsExecuted).isEqualTo(4);

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            try (var marker = statement.executeQuery(
                    "select marker_value from legacy_marker where id = 1"
            )) {
                assertThat(marker.next()).isTrue();
                assertThat(marker.getString("marker_value")).isEqualTo("preserved");
            }

            try (var pmsTables = statement.executeQuery("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = database()
                      and left(table_name, 4) = 'pms_'
                    """)) {
                assertThat(pmsTables.next()).isTrue();
                assertThat(pmsTables.getInt(1)).isEqualTo(41);
            }

            try (var workdaySwaps = statement.executeQuery("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = database()
                      and table_name = 'workday_swaps'
                    """)) {
                assertThat(workdaySwaps.next()).isTrue();
                assertThat(workdaySwaps.getInt(1)).isEqualTo(1);
            }

            try (var history = statement.executeQuery("""
                    select version, success
                    from flyway_schema_history
                    where version = '15'
                    """)) {
                assertThat(history.next()).isTrue();
                assertThat(history.getString("version")).isEqualTo("15");
                assertThat(history.getBoolean("success")).isTrue();
            }
        }

        String freshUrl = url.replaceFirst(
                "/chrono_migration_test(?=\\?|$)",
                "/chrono_migration_fresh"
        );
        assertThat(freshUrl).isNotEqualTo(url);

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("""
                    create database chrono_migration_fresh
                    character set utf8mb4
                    collate utf8mb4_unicode_ci
                    """);
        }

        var freshResult = Flyway.configure()
                .dataSource(freshUrl, username, password)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load()
                .migrate();

        assertThat(freshResult.migrationsExecuted).isEqualTo(5);

        try (var connection = DriverManager.getConnection(
                freshUrl,
                username,
                password
        );
             var statement = connection.createStatement();
             var pmsTables = statement.executeQuery("""
                     select count(*)
                     from information_schema.tables
                     where table_schema = database()
                       and left(table_name, 4) = 'pms_'
                     """)) {
            assertThat(pmsTables.next()).isTrue();
            assertThat(pmsTables.getInt(1)).isEqualTo(41);
            assertThat(tableExists(connection, "workday_swaps")).isTrue();
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws Exception {
        try (var result = connection.getMetaData().getTables(
                null, null, tableName, new String[]{"TABLE"})) {
            return result.next();
        }
    }
}
