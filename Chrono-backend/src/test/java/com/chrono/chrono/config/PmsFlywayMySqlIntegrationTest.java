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
                .matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):\\d+/chrono_migration_test[a-z0-9_]*(\\?.*)?");

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("create table companies (id bigint primary key)");
            statement.execute("create table users (id bigint primary key)");
            statement.execute("create table time_tracking_entries (id bigint primary key)");
            statement.execute("create table correction_requests (id bigint primary key)");
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

        var flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("14")
                .cleanDisabled(true)
                .load();
        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isPositive().isEqualTo(flyway.info().applied().length - 1);
        assertCurrentMigrations(flyway);

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            try (var marker = statement.executeQuery(
                    "select marker_value from legacy_marker where id = 1"
            )) {
                assertThat(marker.next()).isTrue();
                assertThat(marker.getString("marker_value")).isEqualTo("preserved");
            }

            assertEnterpriseSchema(connection);

            try (var workdaySwaps = statement.executeQuery("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = database()
                      and table_name = 'workday_swaps'
                    """)) {
                assertThat(workdaySwaps.next()).isTrue();
                assertThat(workdaySwaps.getInt(1)).isEqualTo(1);
            }

            assertThat(tableExists(connection, "user_ui_preferences")).isTrue();
            assertThat(tableExists(connection, "pms_front_desk_booking_requests")).isTrue();
            assertThat(columnExists(connection, "pms_guests", "reference_code")).isTrue();
            assertThat(columnExists(connection, "pms_organizations", "master_record")).isTrue();
            assertThat(columnExists(connection, "pms_reservations", "child_ages")).isTrue();

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
                "/chrono_migration_test([a-z0-9_]*)(?=\\?|$)",
                "/chrono_migration_fresh$1"
        );
        assertThat(freshUrl).isNotEqualTo(url);

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            String freshSchema = java.net.URI.create(freshUrl.substring("jdbc:".length())).getPath().substring(1);
            assertThat(freshSchema).matches("chrono_migration_fresh[a-z0-9_]*");
            statement.execute("create database " + freshSchema + " character set utf8mb4 collate utf8mb4_unicode_ci");
        }

        var freshFlyway = Flyway.configure()
                .dataSource(freshUrl, username, password)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
        var freshResult = freshFlyway.migrate();

        assertThat(freshResult.migrationsExecuted).isPositive().isEqualTo(freshFlyway.info().applied().length);
        assertCurrentMigrations(freshFlyway);

        try (var connection = DriverManager.getConnection(
                freshUrl,
                username,
                password
        )) {
            assertEnterpriseSchema(connection);
            assertThat(tableExists(connection, "workday_swaps")).isTrue();
            assertThat(tableExists(connection, "user_ui_preferences")).isTrue();
            assertThat(tableExists(connection, "pms_front_desk_booking_requests")).isTrue();
        }
    }

    private void assertCurrentMigrations(Flyway flyway) {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current()).isNotNull();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private void assertEnterpriseSchema(java.sql.Connection connection) throws Exception {
        for (String table : new String[] {
                "pms_properties", "pms_reservation_room_segments", "pms_reservation_guests",
                "pms_payment_requests", "pms_invoice_lines", "pms_financial_periods",
                "pms_credit_accounts", "pms_receivables", "pms_receivable_settlements",
                "pms_group_allotments", "pms_event_orders", "pms_event_order_lines",
                "pms_housekeeping_task_events", "pms_accounting_settings", "pms_accounting_accounts",
                "pms_revenue_snapshots", "pms_revenue_snapshot_days", "pms_revenue_budgets",
                "pms_billing_settings", "pms_invoice_documents", "pms_delivery_jobs", "pms_bank_imports",
                "pms_bank_transactions", "pms_dunning_notices", "pms_accounting_export_runs",
                "pms_financial_approvals", "pms_approval_policies"
        }) {
            assertThat(tableExists(connection, table)).as(table).isTrue();
        }
        assertThat(columnExists(connection, "pms_payments", "posting_date")).isTrue();
        assertThat(columnExists(connection, "pms_folio_items", "source_reservation_id")).isTrue();
        assertThat(columnExists(connection, "pms_invoice_lines", "active_source_item_id")).isTrue();
        for (String[] column : new String[][] {
                {"pms_folio_items", "total_amount"}, {"pms_payments", "amount"},
                {"pms_invoices", "gross_amount"}, {"pms_rate_plans", "nightly_rate"}
        }) {
            try (var metadata = connection.getMetaData().getColumns(
                    connection.getCatalog(), null, column[0], column[1])) {
                assertThat(metadata.next()).isTrue();
                assertThat(metadata.getInt("DECIMAL_DIGITS")).as(column[0] + "." + column[1]).isEqualTo(4);
            }
        }
        assertRefundStatusWrites(connection);
    }

    private void assertRefundStatusWrites(java.sql.Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            // Empty, isolated test schemas have no hotel fixtures. Keep all real column constraints.
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                statement.executeUpdate("""
                        INSERT INTO pms_payments
                            (id,folio_id,amount,received_at,created_by,kind,method,status,refund_request_id)
                        VALUES (990001,990000,-0.001,CURRENT_TIMESTAMP,'migration-test','REFUND','CARD',
                            'PENDING','migration-refund')
                        """);
                try (var rows = statement.executeQuery(
                        "SELECT status,amount FROM pms_payments WHERE status='PENDING' AND id=990001")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("PENDING");
                    assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("-0.001");
                }
                assertThat(statement.executeUpdate(
                        "UPDATE pms_payments SET status='FAILED' WHERE id=990001")).isEqualTo(1);
                statement.executeUpdate("""
                        INSERT INTO pms_payments
                            (id,folio_id,amount,received_at,created_by,kind,method,status)
                        VALUES (990002,990000,1.123,CURRENT_TIMESTAMP,'migration-test','PAYMENT',
                            'DIRECT_BILL','POSTED')
                        """);
                try (var rows = statement.executeQuery("""
                        SELECT method,amount FROM pms_payments
                        WHERE status='POSTED' AND method='DIRECT_BILL' AND id=990002
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("DIRECT_BILL");
                    assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("1.123");
                }
            } finally {
                statement.executeUpdate("DELETE FROM pms_payments WHERE id IN (990001,990002)");
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws Exception {
        try (var result = connection.getMetaData().getTables(
                connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private boolean columnExists(java.sql.Connection connection, String tableName, String columnName)
            throws Exception {
        try (var result = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
            return result.next();
        }
    }
}
