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

        var flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load();
        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isPositive().isEqualTo(flyway.info().applied().length);
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "pms_properties")).isTrue();
            assertEnterpriseSchema(connection);
            assertThat(tableExists(connection, "pms_profile_documents")).isTrue();
            assertThat(columnExists(connection, "pms_properties", "invoice_prefix")).isTrue();
            assertThat(columnExists(connection, "pms_properties", "tax_number")).isTrue();
            assertThat(columnExists(connection, "pms_organizations", "contacts")).isTrue();
            assertThat(columnExists(connection, "pms_guests", "billing_profile")).isTrue();
            assertThat(columnExists(connection, "pms_rate_plans", "vat_rate")).isTrue();
            assertThat(tableExists(connection, "pms_reservations")).isTrue();
            assertThat(tableExists(connection, "pms_integration_outbox")).isTrue();
            assertThat(tableExists(connection, "pms_audit_events")).isTrue();
            assertThat(tableExists(connection, "pms_public_booking_requests")).isTrue();
            assertThat(tableExists(connection, "pms_public_rate_limits")).isTrue();
            assertThat(tableExists(connection, "pms_front_desk_booking_requests")).isTrue();
            assertThat(tableExists(connection, "workday_swaps")).isTrue();
            assertThat(tableExists(connection, "user_ui_preferences")).isTrue();
            assertThat(columnExists(connection, "pms_integration_outbox", "next_attempt_at")).isTrue();
            assertThat(columnExists(connection, "pms_integration_outbox", "lock_owner")).isTrue();
            assertThat(columnExists(connection, "user_ui_preferences", "revision")).isTrue();
            assertThat(columnExists(connection, "pms_guests", "reference_code")).isTrue();
            assertThat(columnExists(connection, "pms_guests", "room_preferences")).isTrue();
            assertThat(columnExists(connection, "pms_reservations", "child_ages")).isTrue();
            assertThat(columnExists(connection, "pms_rooms", "features")).isTrue();
            assertThat(foreignKeyExists(connection, "user_ui_preferences", "fk_ui_pref_user", "users")).isTrue();
            assertThat(foreignKeyExists(connection, "user_ui_preferences", "fk_ui_pref_company", "companies")).isTrue();
            assertThat(foreignKeyExists(connection, "user_ui_preferences", "fk_ui_pref_property", "pms_properties")).isTrue();
            assertThat(foreignKeyCascadesOnDelete(connection, "user_ui_preferences", "fk_ui_pref_user")).isTrue();
            assertThat(foreignKeyCascadesOnDelete(connection, "user_ui_preferences", "fk_ui_pref_company")).isTrue();
            assertThat(foreignKeyCascadesOnDelete(connection, "user_ui_preferences", "fk_ui_pref_property")).isTrue();
            assertThat(uniqueIndexExists(
                    connection,
                    "user_ui_preferences",
                    "uk_ui_pref_user_tenant_area_context"
            )).isTrue();
        }
    }

    @Test
    void baselinesAnExistingDatabaseAndAddsOnlyTheMissingPmsSchema() throws Exception {
        String url = "jdbc:h2:mem:chrono_flyway_existing;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("create table companies (id bigint primary key)");
            statement.execute("create table users (id bigint primary key)");
            statement.execute("create table time_tracking_entries (id bigint primary key)");
            statement.execute("create table correction_requests (id bigint primary key)");
            statement.execute("create table legacy_marker (id bigint primary key, marker_value varchar(32))");
            statement.execute("insert into legacy_marker (id, marker_value) values (1, 'preserved')");
        }

        var flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("14")
                .load();
        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isPositive().isEqualTo(flyway.info().applied().length - 1);
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            assertThat(tableExists(connection, "pms_properties")).isTrue();
            assertEnterpriseSchema(connection);
            assertThat(tableExists(connection, "pms_reservations")).isTrue();
            assertThat(tableExists(connection, "pms_integration_outbox")).isTrue();
            assertThat(tableExists(connection, "pms_audit_events")).isTrue();
            assertThat(tableExists(connection, "pms_public_booking_requests")).isTrue();
            assertThat(tableExists(connection, "pms_public_rate_limits")).isTrue();
            assertThat(tableExists(connection, "pms_front_desk_booking_requests")).isTrue();
            assertThat(tableExists(connection, "workday_swaps")).isTrue();
            assertThat(tableExists(connection, "user_ui_preferences")).isTrue();
            assertThat(columnExists(connection, "pms_integration_outbox", "next_attempt_at")).isTrue();
            assertThat(columnExists(connection, "pms_integration_outbox", "lock_owner")).isTrue();
            assertThat(columnExists(connection, "user_ui_preferences", "revision")).isTrue();
            assertThat(columnExists(connection, "pms_guests", "reference_code")).isTrue();
            assertThat(columnExists(connection, "pms_organizations", "master_record")).isTrue();
            assertThat(columnExists(connection, "pms_reservations", "guest_preference_snapshot")).isTrue();
            assertThat(foreignKeyExists(connection, "user_ui_preferences", "fk_ui_pref_user", "users")).isTrue();
            assertThat(foreignKeyExists(connection, "user_ui_preferences", "fk_ui_pref_company", "companies")).isTrue();
            assertThat(foreignKeyExists(connection, "user_ui_preferences", "fk_ui_pref_property", "pms_properties")).isTrue();
            assertThat(foreignKeyCascadesOnDelete(connection, "user_ui_preferences", "fk_ui_pref_user")).isTrue();
            assertThat(foreignKeyCascadesOnDelete(connection, "user_ui_preferences", "fk_ui_pref_company")).isTrue();
            assertThat(foreignKeyCascadesOnDelete(connection, "user_ui_preferences", "fk_ui_pref_property")).isTrue();
            assertThat(uniqueIndexExists(
                    connection,
                    "user_ui_preferences",
                    "uk_ui_pref_user_tenant_area_context"
            )).isTrue();

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

    private void assertEnterpriseSchema(java.sql.Connection connection) throws Exception {
        assertThat(tableExists(connection, "pms_reservation_room_segments")).isTrue();
        assertThat(tableExists(connection, "pms_reservation_guests")).isTrue();
        assertThat(tableExists(connection, "pms_payment_requests")).isTrue();
        assertThat(tableExists(connection, "pms_invoice_lines")).isTrue();
        assertThat(columnExists(connection, "pms_payments", "posting_date")).isTrue();
        assertThat(columnExists(connection, "pms_reservations", "policy_snapshot_at")).isTrue();
        assertThat(foreignKeyExists(connection, "pms_payment_requests", "fk_pms_payment_request_folio", "pms_folios")).isTrue();
        assertThat(uniqueIndexExists(connection, "pms_payment_requests", "uk_pms_payment_request_key")).isTrue();
        for (String[] column : new String[][] {{"pms_folio_items", "total_amount"}, {"pms_payments", "amount"}, {"pms_invoices", "gross_amount"}, {"pms_rate_plans", "nightly_rate"}}) {
            try (var metadata = connection.getMetaData().getColumns(null, null, column[0], column[1])) {
                assertThat(metadata.next()).isTrue();
                assertThat(metadata.getInt("DECIMAL_DIGITS")).as(column[0] + "." + column[1]).isEqualTo(4);
            }
        }
        assertRefundStatusWrites(connection);
    }

    private void assertRefundStatusWrites(java.sql.Connection connection) throws Exception {
        try(var statement=connection.createStatement()) {
            // The isolated schema has no hotel fixtures; disable only FK checks, keeping real column types and constraints.
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            try {
                statement.executeUpdate("INSERT INTO pms_payments(id,folio_id,amount,received_at,created_by,kind,method,status,refund_request_id) VALUES(990001,990000,-0.001,CURRENT_TIMESTAMP,'migration-test','REFUND','CARD','PENDING','migration-refund')");
                try(var rows=statement.executeQuery("SELECT status,amount FROM pms_payments WHERE status='PENDING' AND id=990001")) {
                    assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("PENDING");assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("-0.001");
                }
                assertThat(statement.executeUpdate("UPDATE pms_payments SET status='FAILED' WHERE id=990001")).isEqualTo(1);
                statement.executeUpdate("INSERT INTO pms_payments(id,folio_id,amount,received_at,created_by,kind,method,status) VALUES(990002,990000,1.123,CURRENT_TIMESTAMP,'migration-test','PAYMENT','DIRECT_BILL','POSTED')");
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM pms_payments WHERE status='FAILED' OR method='DIRECT_BILL'")) {
                    assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(2);
                }
            } finally {
                statement.executeUpdate("DELETE FROM pms_payments WHERE id IN (990001,990002)");
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
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

    private boolean foreignKeyExists(
            java.sql.Connection connection,
            String tableName,
            String foreignKeyName,
            String referencedTable
    ) throws Exception {
        try (var result = connection.getMetaData().getImportedKeys(null, null, tableName)) {
            while (result.next()) {
                if (foreignKeyName.equalsIgnoreCase(result.getString("FK_NAME"))
                        && referencedTable.equalsIgnoreCase(result.getString("PKTABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean uniqueIndexExists(
            java.sql.Connection connection,
            String tableName,
            String indexName
    ) throws Exception {
        try (var statement = connection.prepareStatement("""
                select count(*)
                from information_schema.table_constraints
                where lower(table_name) = lower(?)
                  and lower(constraint_name) = lower(?)
                  and constraint_type = 'UNIQUE'
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private boolean foreignKeyCascadesOnDelete(
            java.sql.Connection connection,
            String tableName,
            String foreignKeyName
    ) throws Exception {
        try (var result = connection.getMetaData().getImportedKeys(null, null, tableName)) {
            while (result.next()) {
                if (foreignKeyName.equalsIgnoreCase(result.getString("FK_NAME"))) {
                    return result.getShort("DELETE_RULE") == java.sql.DatabaseMetaData.importedKeyCascade;
                }
            }
            return false;
        }
    }
}
