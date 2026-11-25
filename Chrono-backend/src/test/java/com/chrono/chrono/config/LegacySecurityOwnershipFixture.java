package com.chrono.chrono.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic pre-security-upgrade records; no application startup or real tenant data is involved. */
final class LegacySecurityOwnershipFixture {
    private record OriginalTable(List<String> columns, List<List<String>> rows) {}

    private static final List<String> ORIGINAL_TABLES = List.of(
            "companies", "users", "payslips", "time_tracking_entries", "time_tracking",
            "vacation_requests", "schedule_entries", "gl_accounts", "gl_journal_entries",
            "gl_journal_entry_lines", "fa_assets", "schedule_rules", "nfc_commands");

    private final Map<String, OriginalTable> originals;

    private LegacySecurityOwnershipFixture(Map<String, OriginalTable> originals) {
        this.originals = originals;
    }

    static LegacySecurityOwnershipFixture create(Statement statement) throws SQLException {
        // The integration test deliberately starts with minimal companies/users/payslips tables.
        // Add only historical B14 columns, leaving the correction fixture's rows and FKs intact.
        statement.execute("alter table companies add column name varchar(255)");
        statement.execute("insert into companies(id,name) values(810001,'Synthetic Praxis'),(810002,'Synthetic Other')");
        statement.execute("alter table users add column company_id bigint");
        statement.execute("alter table users add column username varchar(255)");
        statement.execute("alter table users add column tracking_balance_in_minutes integer not null default 0");
        statement.execute("alter table users add column monthly_salary double");
        statement.execute("""
                insert into users(id,company_id,username,tracking_balance_in_minutes,monthly_salary) values
                    (810001,810001,'synthetic.praxis.employee',-385,8000.01),
                    (810002,810002,'synthetic.other.employee',725,6400.07)
                """);
        statement.execute("alter table payslips add column user_id bigint");
        statement.execute("alter table payslips add column deductions double");
        statement.execute("alter table payslips add column employer_contributions double");
        statement.execute("alter table payslips add column overtime_hours double");
        statement.execute("alter table payslips add column payout_overtime bit not null default 0");
        statement.execute("alter table payslips add column locked bit not null default 0");
        statement.execute("alter table payslips add column period_start date");
        statement.execute("alter table payslips add column period_end date");
        statement.execute("alter table payslips add column payout_date date");
        statement.execute("alter table payslips add column version integer");
        statement.execute("""
                insert into payslips(id,user_id,gross_salary,deductions,net_salary,employer_contributions,
                    overtime_hours,payout_overtime,approved,locked,period_start,period_end,payout_date,version) values
                    (810011,810001,8000.00,1200.00,6800.00,600.00,12.75,1,1,1,'2026-09-01','2026-09-30','2026-10-02',7),
                    (810012,810002,-1845.63,-118.12,-1727.51,-177.18,-7.25,1,1,1,'2026-10-01','2026-10-31','2026-11-02',3)
                """);

        statement.execute("alter table time_tracking_entries add column user_id bigint");
        statement.execute("alter table time_tracking_entries add column duration_minutes integer");
        statement.execute("alter table time_tracking_entries add column approved bit not null default 0");
        statement.execute("alter table time_tracking_entries add column corrected_by_user bit not null default 0");
        statement.execute("alter table time_tracking_entries add column description varchar(1000)");
        statement.execute("alter table time_tracking_entries add column system_generated_note varchar(255)");
        statement.execute("alter table time_tracking_entries add column source varchar(32)");
        statement.execute("""
                insert into time_tracking_entries(id,user_id,entry_timestamp,punch_type,duration_minutes,
                    approved,corrected_by_user,description,system_generated_note,source) values
                    (810051,810001,'2026-09-24 07:58:13.123456','START',null,1,0,'Original Praxis arrival',null,'NFC_SCAN'),
                    (810052,810001,'2026-09-24 17:12:19.654321','ENDE',554,1,0,'Original Praxis departure','Original time note','NFC_SCAN'),
                    (810053,810002,'2026-09-24 08:05:00.000001','START',null,1,1,'Original Other arrival',null,'USER_CORRECTION'),
                    (810054,810002,'2026-09-24 16:35:00.000002','ENDE',510,1,0,'Original Other departure',null,'MANUAL_PUNCH')
                """);
        statement.execute("""
                create table time_tracking(
                    id bigint primary key,user_id bigint not null,daily_date date not null,
                    work_start time(6),break_start time(6),break_end time(6),work_end time(6),
                    corrected bit not null,daily_note varchar(2000),
                    constraint fk_security_fixture_day_user foreign key(user_id) references users(id))
                """);
        statement.execute("""
                insert into time_tracking values
                    (810055,810001,'2026-09-24','07:58:13','12:00:00','12:30:00','17:12:19',0,'Original day with overtime'),
                    (810056,810002,'2026-09-24','08:05:00','12:00:00','12:30:00','16:35:00',1,'Original corrected day')
                """);
        statement.execute("""
                create table vacation_requests(
                    id bigint primary key,user_id bigint,approved bit not null,denied bit not null,
                    company_vacation bit not null,half_day bit not null,start_date date,end_date date,
                    uses_overtime bit not null,overtime_deduction_minutes integer,admin_note varchar(1000),
                    constraint fk_security_fixture_vacation_user foreign key(user_id) references users(id))
                """);
        statement.execute("""
                insert into vacation_requests values
                    (810061,810001,1,0,0,1,'2026-10-08','2026-10-08',1,240,'Original approved overtime compensation'),
                    (810062,810002,1,0,0,0,'2026-10-09','2026-10-09',1,480,'Original Other overtime compensation')
                """);
        statement.execute("""
                create table schedule_entries(
                    id bigint primary key,user_id bigint not null,date date not null,shift varchar(255) not null,
                    constraint fk_security_fixture_schedule_user foreign key(user_id) references users(id))
                """);
        statement.execute("insert into schedule_entries values(810063,810001,'2026-10-08','EARLY'),(810064,810002,'2026-10-08','EARLY')");

        statement.execute("""
                create table gl_accounts(
                    id bigint primary key,code varchar(32) not null,name varchar(255) not null,
                    type varchar(32) not null,parent_id bigint,active bit not null,notes varchar(512),
                    constraint UK3j5taib3t5ac21n91xs64p0s2 unique(code),
                    constraint fk_security_fixture_account_parent foreign key(parent_id) references gl_accounts(id))
                """);
        statement.execute("""
                insert into gl_accounts values
                    (810091,'1000','Historische gemeinsame Bank','ASSET',null,1,'Original private historical notes'),
                    (810092,'5000','Historische Löhne','EXPENSE',810091,1,'Original wages'),
                    (810093,'5100','Historische Arbeitgeberbeiträge','EXPENSE',null,0,'Original inactive account'),
                    (810094,'2000','Historische Personalverbindlichkeit','LIABILITY',null,1,null),
                    (810095,'2300','Historische Sozialversicherung','LIABILITY',null,1,'Original social liabilities')
                """);
        statement.execute("""
                create table gl_journal_entries(
                    id bigint primary key,entry_date date not null,description varchar(512),source varchar(128),
                    document_reference varchar(128),created_at datetime(6) not null)
                """);
        statement.execute("""
                insert into gl_journal_entries values
                    (810071,'2026-09-30','Original positive payroll','PAYROLL','PAYSLIP-810011','2026-10-02 12:13:14.123456'),
                    (810072,'2026-10-31','Original negative payroll','PAYROLL','PAYSLIP-810012','2026-11-02 14:15:16.654321'),
                    (810073,'2024-01-17','Original asset acquisition','ASSET','ASSET-810081','2024-01-17 10:11:12.345678')
                """);
        statement.execute("""
                create table gl_journal_entry_lines(
                    id bigint primary key,journal_entry_id bigint not null,account_id bigint not null,
                    debit decimal(19,4),credit decimal(19,4),memo varchar(256),
                    constraint fk_security_fixture_line_entry foreign key(journal_entry_id) references gl_journal_entries(id),
                    constraint fk_security_fixture_line_account foreign key(account_id) references gl_accounts(id))
                """);
        statement.execute("""
                insert into gl_journal_entry_lines values
                    (810101,810071,810092,8000.0000,0.0000,'Original wages debit'),
                    (810102,810071,810093,600.0000,0.0000,'Original employer debit'),
                    (810103,810071,810091,0.0000,6800.0000,'Original net payout'),
                    (810104,810071,810094,0.0000,1200.0000,'Original employee deductions'),
                    (810105,810071,810095,0.0000,600.0000,'Original employer credit'),
                    (810106,810072,810092,0.0000,1845.6300,'Original negative wages'),
                    (810107,810072,810093,0.0000,177.1800,'Original negative employer debit'),
                    (810108,810072,810091,1727.5100,0.0000,'Original negative net payout'),
                    (810109,810072,810094,118.1200,0.0000,'Original negative employee deductions'),
                    (810110,810072,810095,177.1800,0.0000,'Original negative employer credit'),
                    (810111,810073,810092,6000.1234,0.0000,'Original asset debit'),
                    (810112,810073,810091,0.0000,6000.1234,'Original acquisition payment')
                """);
        statement.execute("""
                create table fa_assets(
                    id bigint primary key,asset_name varchar(255) not null,acquisition_date date not null,
                    acquisition_cost decimal(19,4) not null,useful_life_months integer not null,
                    residual_value decimal(19,4),status varchar(32) not null,accumulated_depreciation decimal(19,4),
                    last_depreciation_date date,journal_entry_id bigint,
                    constraint fk_security_fixture_asset_entry foreign key(journal_entry_id) references gl_journal_entries(id))
                """);
        statement.execute("""
                insert into fa_assets values
                    (810081,'Historische Anlage','2024-01-17',6000.1234,60,100.1250,'ACTIVE',1500.4321,'2026-09-30',810073)
                """);
        statement.execute("""
                create table schedule_rules(
                    id bigint primary key,shift_key varchar(255) not null,label varchar(255) not null,
                    start_time varchar(255) not null,end_time varchar(255) not null,is_active bit not null,
                    constraint uk_security_fixture_global_shift unique(shift_key))
                """);
        statement.execute("insert into schedule_rules values(810121,'EARLY','Historische Frühschicht','07:30','16:15',1)");
        statement.execute("""
                create table nfc_commands(
                    id bigint primary key,type varchar(255),data varchar(255),status varchar(255),
                    created_at datetime(6),processed_at datetime(6))
                """);
        statement.execute("""
                insert into nfc_commands values
                    (810131,'PROGRAM','synthetic.praxis.employee','pending','2026-10-06 11:12:13.123456',null),
                    (810132,'PROGRAM','00112233445566778899AABBCCDDEEFF','done','2026-10-05 10:11:12.654321','2026-10-05 10:11:14.123456')
                """);

        Map<String, OriginalTable> originals = new LinkedHashMap<>();
        for (String table : ORIGINAL_TABLES) {
            List<String> columns = new ArrayList<>();
            try (var rows = statement.executeQuery("select * from " + table + " order by id")) {
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                    columns.add(rows.getMetaData().getColumnName(column));
                }
            }
            originals.put(table, new OriginalTable(columns, originalRows(statement.getConnection(), table, columns)));
        }
        return new LegacySecurityOwnershipFixture(originals);
    }

    void assertOriginalsPreserved(Connection connection) throws SQLException {
        for (var original : originals.entrySet()) {
            assertThat(originalRows(connection, original.getKey(), original.getValue().columns()))
                    .as("Every historical column and row ID in " + original.getKey() + " survives all migrations unchanged")
                    .isEqualTo(original.getValue().rows());
        }
        try (var statement = connection.createStatement()) {
            for (String table : List.of("gl_accounts", "gl_journal_entries", "fa_assets", "schedule_rules", "nfc_commands")) {
                try (var rows = statement.executeQuery("select count(*) from " + table + " where company_id is not null")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).as("No guessed legacy company in " + table).isZero();
                }
            }
            try (var rows = statement.executeQuery("select count(*) from nfc_commands where device_id is not null")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).as("Raw command data does not establish a device owner").isZero();
            }
            try (var rows = statement.executeQuery("select count(*) from nfc_commands where target_user_id is not null")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).as("Legacy username data does not establish an immutable target identity").isZero();
            }
            try (var rows = statement.executeQuery("select suppressed,owner_scope from schedule_rules where id=810121")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean("suppressed")).isFalse();
                assertThat(rows.getLong("owner_scope")).isZero();
                assertThat(rows.next()).isFalse();
            }
        }
        assertShiftOwnershipSchemaAndCompanyDeletion(connection);
    }

    private void assertShiftOwnershipSchemaAndCompanyDeletion(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var column = statement.executeQuery("""
                select extra from information_schema.columns
                where table_schema=database() and table_name='schedule_rules' and column_name='owner_scope'
                """)) {
            assertThat(column.next()).isTrue();
            assertThat(column.getString("extra")).as("MySQL CASCADE requires a virtual ownership expression")
                    .contains("VIRTUAL GENERATED").doesNotContain("STORED");
            assertThat(column.next()).isFalse();
        }
        // Exercise only fresh synthetic IDs after snapshot preservation was checked.
        // Roll back this scenario so no original fixture row or later assertion changes.
        assertThat(connection.getAutoCommit()).isTrue();
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.execute("insert into companies(id,name) values(810901,'Cascade scenario A'),(810902,'Cascade scenario B')");
            statement.execute("""
                    insert into schedule_rules(id,shift_key,label,start_time,end_time,is_active,company_id)
                    values(810911,'EARLY','Owned A','08:00','12:00',1,810901),
                          (810912,'EARLY','Owned B','12:00','16:00',1,810902)
                    """);
            org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> statement.execute("""
                    insert into schedule_rules(id,shift_key,label,start_time,end_time,is_active,company_id)
                    values(810913,'EARLY','Duplicate owned','08:00','12:00',1,810901)
                    """));
            org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> statement.execute("""
                    insert into schedule_rules(id,shift_key,label,start_time,end_time,is_active,company_id)
                    values(810914,'EARLY','Duplicate default','08:00','12:00',1,null)
                    """));
            statement.execute("""
                    insert into nfc_commands(id,type,data,status,company_id,device_id)
                    values(810921,'PROGRAM','synthetic-owned-user','pending',810901,'synthetic-reader')
                    """);
            statement.execute("delete from companies where id=810901");
            try (var owned = statement.executeQuery("select count(*) from schedule_rules where company_id=810901")) {
                assertThat(owned.next()).isTrue(); assertThat(owned.getLong(1)).isZero();
            }
            try (var other = statement.executeQuery("select company_id,owner_scope,label from schedule_rules where id=810912")) {
                assertThat(other.next()).isTrue(); assertThat(other.getLong("company_id")).isEqualTo(810902L);
                assertThat(other.getLong("owner_scope")).isEqualTo(810902L); assertThat(other.getString("label")).isEqualTo("Owned B");
            }
            try (var command = statement.executeQuery("select company_id,device_id,data,status from nfc_commands where id=810921")) {
                assertThat(command.next()).isTrue(); assertThat(command.getObject("company_id")).isNull();
                assertThat(command.getString("device_id")).isEqualTo("synthetic-reader");
                assertThat(command.getString("data")).isEqualTo("synthetic-owned-user");
                assertThat(command.getString("status")).isEqualTo("pending");
            }
            for (var original : originals.entrySet()) {
                // New synthetic scenario rows are excluded; existing rows remain identical.
                var current = originalRows(connection, original.getKey(), original.getValue().columns());
                assertThat(current).containsAll(original.getValue().rows());
            }
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private static List<List<String>> originalRows(Connection connection, String table, List<String> columns)
            throws SQLException {
        String projection = columns.stream().map(column -> "`" + column.replace("`", "``") + "`")
                .collect(java.util.stream.Collectors.joining(","));
        List<List<String>> values = new ArrayList<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("select " + projection + " from " + table + " order by id")) {
            while (rows.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= columns.size(); column++) row.add(rows.getString(column));
                values.add(row);
            }
        }
        return values;
    }
}
