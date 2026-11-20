package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Preserve existing defaults/commands without guessing their company ownership. */
public class V59__tenant_shift_and_nfc_ownership extends BaseJavaMigration {
    private record Table(String catalog, String schema, String name) {}

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Table shifts = table(connection, "schedule_rules");
        if (shifts != null) {
            addColumn(connection, shifts, "company_id", "bigint null");
            addColumn(connection, shifts, "suppressed", "bit not null default 0");
            addForeignKey(connection, shifts, "fk_schedule_rules_company", "on delete cascade");
            removeGlobalShiftKeyUniqueness(connection, shifts);
            // MySQL nullable composite keys would allow duplicate shared defaults.
            // MySQL prohibits CASCADE on a FK base column of a STORED generated
            // column. An indexed VIRTUAL column preserves both uniqueness and
            // the existing company-owned definition deletion semantics.
            String generatedStorage = isH2(connection) ? "" : " virtual";
            addColumn(connection, shifts, "owner_scope", "bigint generated always as (coalesce(company_id, 0))" + generatedStorage);
            if (!hasIndex(connection, shifts, "uk_schedule_rules_owner_key")) {
                execute(connection, "alter table schedule_rules add constraint uk_schedule_rules_owner_key unique(owner_scope, shift_key)");
            }
        }
        Table commands = table(connection, "nfc_commands");
        if (commands != null) {
            addColumn(connection, commands, "company_id", "bigint null");
            // No FK: deleting or renaming a target must retain the original
            // intent ID for dispatch cancellation, never retarget the command.
            addColumn(connection, commands, "target_user_id", "bigint null");
            addColumn(connection, commands, "device_id", "varchar(100) null");
            addForeignKey(connection, commands, "fk_nfc_commands_company", "on delete set null");
            if (!hasIndex(connection, commands, "idx_nfc_commands_owner_pending")) {
                execute(connection, "create index idx_nfc_commands_owner_pending on nfc_commands(company_id, device_id, status, created_at, id)");
            }
        }
        // No UPDATE/DELETE statements: legacy rows remain NULL-owned. Payroll,
        // overtime, time entries, schedules, raw keys and command data are untouched.
    }

    private void removeGlobalShiftKeyUniqueness(Connection connection, Table table) throws SQLException {
        String schema = isH2(connection) ? table.schema() : table.catalog();
        List<String> constraints = new ArrayList<>();
        try (var query = connection.prepareStatement("""
                select constraint_name from information_schema.table_constraints
                where table_schema = ? and table_name = ? and constraint_type = 'UNIQUE'
                """)) {
            query.setString(1, schema); query.setString(2, table.name());
            try (var rows = query.executeQuery()) { while (rows.next()) constraints.add(rows.getString(1)); }
        }
        for (String constraint : constraints) {
            List<String> columns = new ArrayList<>();
            try (var query = connection.prepareStatement("""
                    select column_name from information_schema.key_column_usage
                    where table_schema = ? and table_name = ? and constraint_name = ?
                    order by ordinal_position
                    """)) {
                query.setString(1, schema); query.setString(2, table.name()); query.setString(3, constraint);
                try (var rows = query.executeQuery()) { while (rows.next()) columns.add(rows.getString(1)); }
            }
            if (columns.size() == 1 && "shift_key".equalsIgnoreCase(columns.get(0))) {
                execute(connection, "alter table schedule_rules " + (isH2(connection) ? "drop constraint " : "drop index ")
                        + "`" + constraint.replace("`", "``") + "`");
            }
        }
    }

    private Table table(Connection connection, String name) throws SQLException {
        try (var rows = connection.getMetaData().getTables(connection.getCatalog(), null, null, null)) {
            while (rows.next()) if (name.equalsIgnoreCase(rows.getString("TABLE_NAME"))) {
                return new Table(rows.getString("TABLE_CAT"), rows.getString("TABLE_SCHEM"), rows.getString("TABLE_NAME"));
            }
        }
        return null;
    }

    private void addColumn(Connection connection, Table table, String name, String definition) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(table.catalog(), table.schema(), table.name(), null)) {
            while (columns.next()) if (name.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return;
        }
        execute(connection, "alter table " + table.name() + " add column " + name + " " + definition);
    }

    private void addForeignKey(Connection connection, Table table, String name, String deletion) throws SQLException {
        try (var keys = connection.getMetaData().getImportedKeys(table.catalog(), table.schema(), table.name())) {
            while (keys.next()) if (name.equalsIgnoreCase(keys.getString("FK_NAME"))) return;
        }
        execute(connection, "alter table " + table.name() + " add constraint " + name
                + " foreign key(company_id) references companies(id) " + deletion);
    }

    private boolean hasIndex(Connection connection, Table table, String name) throws SQLException {
        try (var indexes = connection.getMetaData().getIndexInfo(table.catalog(), table.schema(), table.name(), false, false)) {
            while (indexes.next()) {
                String index = indexes.getString("INDEX_NAME");
                if (index != null && (name.equalsIgnoreCase(index) || index.toUpperCase().startsWith(name.toUpperCase() + "_INDEX_"))) return true;
            }
        }
        return false;
    }

    private boolean isH2(Connection connection) throws SQLException {
        return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
