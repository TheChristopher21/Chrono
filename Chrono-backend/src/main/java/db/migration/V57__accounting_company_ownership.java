package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Add ownership without guessing the owner of any historical financial row. */
public class V57__accounting_company_ownership extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        for (String table : List.of("gl_accounts", "gl_journal_entries", "fa_assets")) {
            if (!tableExists(connection, table)) continue;
            if (!columnExists(connection, table, "company_id")) {
                execute(connection, "alter table " + table + " add column company_id bigint null");
            }
            if (!foreignKeyExists(connection, table, "fk_" + table + "_company")) {
                execute(connection, "alter table " + table + " add constraint fk_" + table
                        + "_company foreign key (company_id) references companies(id)");
            }
        }
        if (tableExists(connection, "gl_accounts")) {
            removeGlobalCodeUniqueness(connection);
            if (!indexExists(connection, "gl_accounts", "uk_gl_account_company_code")) {
                execute(connection, "alter table gl_accounts add constraint uk_gl_account_company_code unique(company_id, code)");
            }
        }
        if (tableExists(connection, "gl_journal_entries")
                && !indexExists(connection, "gl_journal_entries", "idx_gl_journal_company_date")) {
            execute(connection, "create index idx_gl_journal_company_date on gl_journal_entries(company_id, entry_date)");
        }
        if (tableExists(connection, "fa_assets") && !indexExists(connection, "fa_assets", "idx_fa_asset_company")) {
            execute(connection, "create index idx_fa_asset_company on fa_assets(company_id)");
        }
        // Deliberately no UPDATE/DELETE: source/document_reference and account
        // codes were user-editable and cannot prove historical company ownership.
    }

    private void removeGlobalCodeUniqueness(Connection connection) throws SQLException {
        List<String> constraints = new ArrayList<>();
        String schema = connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")
                ? connection.getSchema() : connection.getCatalog();
        try (var query = connection.prepareStatement("""
                select constraint_name from information_schema.table_constraints
                where table_schema = ? and upper(table_name) = 'GL_ACCOUNTS' and constraint_type = 'UNIQUE'
                """)) {
            query.setString(1, schema);
            try (var rows = query.executeQuery()) {
                while (rows.next()) constraints.add(rows.getString(1));
            }
        }
        for (String constraint : constraints) {
            List<String> columns = new ArrayList<>();
            try (var query = connection.prepareStatement("""
                    select column_name from information_schema.key_column_usage
                    where table_schema = ? and upper(table_name) = 'GL_ACCOUNTS' and constraint_name = ?
                    order by ordinal_position
                    """)) {
                query.setString(1, schema);
                query.setString(2, constraint);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) columns.add(rows.getString(1));
                }
            }
            if (columns.size() != 1 || !"code".equalsIgnoreCase(columns.get(0))) continue;
            if (!constraint.matches("[A-Za-z0-9_]+")) throw new SQLException("Unexpected account constraint identifier");
            String operation = connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")
                    ? " drop constraint " : " drop index ";
            execute(connection, "alter table gl_accounts" + operation + constraint);
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (var rows = connection.getMetaData().getTables(connection.getCatalog(), null, null, null)) {
            while (rows.next()) if (table.equalsIgnoreCase(rows.getString("TABLE_NAME"))) return true;
        }
        return false;
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), null, null, null)) {
            while (rows.next()) if (table.equalsIgnoreCase(rows.getString("TABLE_NAME"))
                    && column.equalsIgnoreCase(rows.getString("COLUMN_NAME"))) return true;
        }
        return false;
    }

    private boolean indexExists(Connection connection, String table, String index) throws SQLException {
        try (var rows = connection.getMetaData().getTables(connection.getCatalog(), null, null, null)) {
            while (rows.next()) {
                if (!table.equalsIgnoreCase(rows.getString("TABLE_NAME"))) continue;
                try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(),
                        rows.getString("TABLE_SCHEM"), rows.getString("TABLE_NAME"), false, false)) {
                    while (indexes.next()) {
                        String name = indexes.getString("INDEX_NAME");
                        if (name != null && (index.equalsIgnoreCase(name)
                                || name.toUpperCase().startsWith(index.toUpperCase() + "_INDEX_"))) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean foreignKeyExists(Connection connection, String table, String key) throws SQLException {
        try (var rows = connection.getMetaData().getTables(connection.getCatalog(), null, null, null)) {
            while (rows.next()) {
                if (!table.equalsIgnoreCase(rows.getString("TABLE_NAME"))) continue;
                try (var keys = connection.getMetaData().getImportedKeys(connection.getCatalog(),
                        rows.getString("TABLE_SCHEM"), rows.getString("TABLE_NAME"))) {
                    while (keys.next()) if (key.equalsIgnoreCase(keys.getString("FK_NAME"))) return true;
                }
            }
        }
        return false;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
