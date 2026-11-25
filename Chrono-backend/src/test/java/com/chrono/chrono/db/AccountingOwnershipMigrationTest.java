package com.chrono.chrono.db;

import db.migration.V57__accounting_company_ownership;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountingOwnershipMigrationTest {
    private static final Map<String, String> ORIGINAL_QUERIES = new LinkedHashMap<>();
    static {
        ORIGINAL_QUERIES.put("gl_accounts", "select id,code,name,type,parent_id,active,notes from gl_accounts order by id");
        ORIGINAL_QUERIES.put("gl_journal_entries", "select id,entry_date,description,source,document_reference,created_at from gl_journal_entries order by id");
        ORIGINAL_QUERIES.put("gl_journal_entry_lines", "select id,journal_entry_id,account_id,debit,credit,memo from gl_journal_entry_lines order by id");
        ORIGINAL_QUERIES.put("fa_assets", "select id,asset_name,acquisition_date,acquisition_cost,useful_life_months,residual_value,status,accumulated_depreciation,last_depreciation_date,journal_entry_id from fa_assets order by id");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyRowsKeepEveryValueAndTheirIdsWithoutGuessingACompany(boolean lowercaseIdentifiers) throws Exception {
        String settings = lowercaseIdentifiers ? ";DATABASE_TO_LOWER=TRUE" : "";
        try (Connection connection = database(settings)) {
            createLegacyAccounting(connection);
            Map<String, List<List<String>>> original = snapshot(connection);

            migrate(connection);

            assertThat(snapshot(connection)).isEqualTo(original);
            for (String table : List.of("gl_accounts", "gl_journal_entries", "fa_assets")) {
                assertThat(rows(connection, "select company_id from " + table + " order by id"))
                        .as(table + " remains unassigned despite matching account codes and a payroll reference")
                        .allSatisfy(row -> assertThat(row).containsExactly((String) null));
            }
            // Metadata guards must also permit a safe repeat without reassigning or rewriting history.
            migrate(connection);
            assertThat(snapshot(connection)).isEqualTo(original);

            execute(connection, "insert into gl_accounts(id,company_id,code,name,type,active) values(610,17,'1000','Praxis bank','ASSET',1)");
            execute(connection, "insert into gl_accounts(id,company_id,code,name,type,active) values(611,29,'1000','Other bank','ASSET',1)");
            assertThat(rows(connection, "select id,company_id,code,name from gl_accounts where company_id is not null order by id"))
                    .containsExactly(List.of("610", "17", "1000", "Praxis bank"), List.of("611", "29", "1000", "Other bank"));
            assertThatThrownBy(() -> execute(connection,
                    "insert into gl_accounts(id,company_id,code,name,type,active) values(612,17,'1000','Duplicate Praxis bank','ASSET',1)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "insert into gl_accounts(id,company_id,code,name,type,active) values(613,999,'1010','Missing company','ASSET',1)"))
                    .isInstanceOf(SQLException.class);
            assertThat(rows(connection, "select id,company_id,name,notes from gl_accounts where id=510"))
                    .containsExactly(java.util.Arrays.asList("510", null, "Historische Bank", "Original bank notes"));
        }
    }

    @Test
    void absentLegacyAccountingTablesAreNotInventedOrRequired() throws Exception {
        try (Connection connection = database("")) {
            execute(connection, "create table companies(id bigint primary key)");
            execute(connection, "insert into companies values(17),(29)");

            migrate(connection);

            assertThat(rows(connection, "select id from companies order by id"))
                    .containsExactly(List.of("17"), List.of("29"));
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, null, new String[]{"TABLE"})) {
                List<String> names = new ArrayList<>();
                while (tables.next()) names.add(tables.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
                assertThat(names).doesNotContain("gl_accounts", "gl_journal_entries", "fa_assets");
            }
        }
    }

    private void createLegacyAccounting(Connection connection) throws SQLException {
        execute(connection, "create table companies(id bigint primary key,name varchar(255))");
        execute(connection, "insert into companies values(17,'Praxis'),(29,'Other')");
        execute(connection, """
                create table gl_accounts(
                    id bigint primary key,code varchar(32) not null,name varchar(255) not null,
                    type varchar(32) not null,parent_id bigint,active boolean not null,notes varchar(512),
                    constraint uk_hibernate_legacy_code_77 unique(code),
                    constraint fk_legacy_parent foreign key(parent_id) references gl_accounts(id))
                """);
        execute(connection, """
                create table gl_journal_entries(
                    id bigint primary key,entry_date date not null,description varchar(512),source varchar(128),
                    document_reference varchar(128),created_at timestamp(6) not null)
                """);
        execute(connection, """
                create table gl_journal_entry_lines(
                    id bigint primary key,journal_entry_id bigint not null,account_id bigint not null,
                    debit decimal(19,4),credit decimal(19,4),memo varchar(256),
                    foreign key(journal_entry_id) references gl_journal_entries(id),
                    foreign key(account_id) references gl_accounts(id))
                """);
        execute(connection, """
                create table fa_assets(
                    id bigint primary key,asset_name varchar(255) not null,acquisition_date date not null,
                    acquisition_cost decimal(19,4) not null,useful_life_months integer not null,
                    residual_value decimal(19,4),status varchar(32) not null,
                    accumulated_depreciation decimal(19,4),last_depreciation_date date,journal_entry_id bigint,
                    foreign key(journal_entry_id) references gl_journal_entries(id))
                """);
        execute(connection, "insert into gl_accounts values(510,'1000','Historische Bank','ASSET',null,1,'Original bank notes')");
        execute(connection, "insert into gl_accounts values(511,'5000','Historische Löhne','EXPENSE',510,0,'Do not clone or reassign')");
        execute(connection, """
                insert into gl_journal_entries values
                    (700,'2026-09-30','Original signed payroll reference','PAYROLL','PAYSLIP-910','2026-09-30 12:13:14.123456'),
                    (701,'2026-10-01','Original manual reference','MANUAL','ARBITRARY-TEXT-17','2026-10-01 14:15:16.654321')
                """);
        execute(connection, """
                insert into gl_journal_entry_lines values
                    (710,700,510,0.0000,200.3417,'Original payroll credit'),
                    (711,700,511,200.3417,0.0000,'Original payroll debit'),
                    (712,701,510,1.2345,0.0000,'Original manual debit'),
                    (713,701,511,0.0000,1.2345,'Original manual credit')
                """);
        execute(connection, """
                insert into fa_assets values
                    (900,'Historische Anlage','2024-01-17',12450.9876,60,100.0001,'ACTIVE',124.5099,'2026-10-01',700)
                """);
    }

    private Connection database(String settings) throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:accounting_ownership_" + UUID.randomUUID()
                + ";MODE=MySQL" + settings, "sa", "");
    }

    private void migrate(Connection connection) throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        new V57__accounting_company_ownership().migrate(context);
    }

    private Map<String, List<List<String>>> snapshot(Connection connection) throws SQLException {
        Map<String, List<List<String>>> snapshot = new LinkedHashMap<>();
        for (var query : ORIGINAL_QUERIES.entrySet()) snapshot.put(query.getKey(), rows(connection, query.getValue()));
        return snapshot;
    }

    private List<List<String>> rows(Connection connection, String query) throws SQLException {
        List<List<String>> values = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) row.add(rows.getString(column));
                values.add(row);
            }
        }
        return values;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
