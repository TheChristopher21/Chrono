package com.chrono.chrono.db;

import db.migration.V59__tenant_shift_and_nfc_ownership;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NfcShiftOwnershipMigrationTest {
    @Test
    void missingOptionalLegacyTablesDoNotBreakMigration() throws Exception {
        try (Connection connection = database()) {
            execute(connection, "create table companies(id bigint primary key, name varchar(255))");
            migrate(connection);
        }
    }

    @Test
    void migrationPreservesRawDefaultsCommandsAndHistoricalTimeWhileScopingNewRows() throws Exception {
        try (Connection connection = database()) {
            execute(connection, "create table companies(id bigint primary key, name varchar(255))");
            execute(connection, "insert into companies values(17, 'Praxis'), (29, 'Other')");
            execute(connection, "create table schedule_rules(id bigint primary key, shift_key varchar(255) not null unique, label varchar(255), start_time varchar(255), end_time varchar(255), is_active bit)");
            execute(connection, "insert into schedule_rules values(5, 'EARLY', 'Legacy label', '07:00', '15:00', 1)");
            execute(connection, "create table nfc_commands(id bigint primary key, data varchar(255), type varchar(255), status varchar(255), created_at timestamp, processed_at timestamp)");
            execute(connection, "insert into nfc_commands values(7, 'legacy-raw-data', 'PROGRAM', 'pending', '2026-10-05 08:00:00', null)");
            execute(connection, "create table time_tracking_entries(id bigint primary key, duration_minutes int, description varchar(255))");
            execute(connection, "insert into time_tracking_entries values(11, 123, 'unchanged')");
            execute(connection, "create table payslips(id bigint primary key, amount decimal(10,2))");
            execute(connection, "insert into payslips values(13, 456.78)");
            migrate(connection);
            assertRow(connection, "select shift_key, label, start_time, end_time from schedule_rules where id=5",
                    "EARLY", "Legacy label", "07:00", "15:00");
            assertRow(connection, "select data, type, status from nfc_commands where id=7", "legacy-raw-data", "PROGRAM", "pending");
            assertRow(connection, "select company_id, device_id, target_user_id from nfc_commands where id=7", null, null, null);
            assertRow(connection, "select company_id from schedule_rules where id=5", (String) null);
            assertRow(connection, "select duration_minutes, description from time_tracking_entries where id=11", "123", "unchanged");
            assertRow(connection, "select amount from payslips where id=13", "456.78");
            execute(connection, "insert into schedule_rules(id, shift_key, company_id) values(6, 'EARLY', 17)");
            execute(connection, "insert into schedule_rules(id, shift_key, company_id) values(7, 'EARLY', 29)");
            assertThrows(SQLException.class, () -> execute(connection,
                    "insert into schedule_rules(id, shift_key, company_id) values(8, 'EARLY', 17)"));
            assertThrows(SQLException.class, () -> execute(connection,
                    "insert into schedule_rules(id, shift_key, company_id) values(9, 'EARLY', null)"));
            // Removing one tenant must not rewrite shared defaults, other tenant
            // definitions, or historical time/payroll rows. Owned commands become
            // quarantined rather than being reassigned to another company.
            execute(connection, "insert into nfc_commands(id, data, type, status, company_id, device_id) values(8, 'owned-data', 'PROGRAM', 'pending', 17, 'praxis-reader')");
            execute(connection, "delete from companies where id=17");
            assertRow(connection, "select count(*) from schedule_rules where company_id=17", "0");
            assertRow(connection, "select shift_key, label, start_time, end_time from schedule_rules where id=5",
                    "EARLY", "Legacy label", "07:00", "15:00");
            assertRow(connection, "select company_id, shift_key from schedule_rules where id=7", "29", "EARLY");
            assertRow(connection, "select company_id, device_id, data, status from nfc_commands where id=8",
                    null, "praxis-reader", "owned-data", "pending");
            assertRow(connection, "select duration_minutes, description from time_tracking_entries where id=11", "123", "unchanged");
            assertRow(connection, "select amount from payslips where id=13", "456.78");
        }
    }

    private Connection database() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:nfc_shifts_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
    }

    private void migrate(Connection connection) throws Exception {
        Context context = mock(Context.class); when(context.getConnection()).thenReturn(connection);
        new V59__tenant_shift_and_nfc_ownership().migrate(context);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private void assertRow(Connection connection, String query, String... values) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            for (int i = 0; i < values.length; i++) assertEquals(values[i], rows.getString(i + 1));
            assertFalse(rows.next());
        }
    }
}
