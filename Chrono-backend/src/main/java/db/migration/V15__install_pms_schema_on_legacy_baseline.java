package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Installs the PMS-only part of the cumulative B14 schema on legacy Chrono
 * databases that were baselined at version 14.
 *
 * <p>The production database already contained the Chrono schema when Flyway
 * was introduced. Flyway correctly recorded a version-14 baseline, but that
 * also meant that the PMS tables from the cumulative B14 schema were not
 * created. This migration is deliberately additive: it only creates missing
 * PMS tables, indexes, unique constraints, and foreign keys.</p>
 */
public class V15__install_pms_schema_on_legacy_baseline extends BaseJavaMigration {

    private static final int EXPECTED_PMS_TABLE_COUNT = 30;
    private static final String BASELINE_RESOURCE =
            "/db/migration/B14__chrono_schema_baseline.sql";

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "^create table\\s+(pms_[a-z0-9_]+)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CREATE_INDEX = Pattern.compile(
            "^create index\\s+([a-z0-9_]+)\\s+on\\s+(pms_[a-z0-9_]+)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ADD_UNIQUE_CONSTRAINT = Pattern.compile(
            "^alter table\\s+(pms_[a-z0-9_]+)\\s+add constraint\\s+"
                    + "([a-z0-9_]+)\\s+unique\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ADD_FOREIGN_KEY = Pattern.compile(
            "^alter table\\s+(pms_[a-z0-9_]+)\\s+add constraint\\s+"
                    + "([a-z0-9_]+)\\s+foreign key\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<String> pmsStatements = loadPmsStatements();
        List<String> expectedTables = pmsStatements.stream()
                .map(CREATE_TABLE::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .toList();

        if (expectedTables.size() != EXPECTED_PMS_TABLE_COUNT) {
            throw new IllegalStateException(
                    "Expected " + EXPECTED_PMS_TABLE_COUNT
                            + " PMS table definitions, found "
                            + expectedTables.size()
            );
        }

        for (String sql : pmsStatements) {
            Matcher createTable = CREATE_TABLE.matcher(sql);
            Matcher createIndex = CREATE_INDEX.matcher(sql);
            Matcher uniqueConstraint = ADD_UNIQUE_CONSTRAINT.matcher(sql);
            Matcher foreignKey = ADD_FOREIGN_KEY.matcher(sql);

            if (createTable.find()) {
                execute(
                        connection,
                        sql.replaceFirst(
                                "(?i)^create table\\s+",
                                "create table if not exists "
                        )
                );
            } else if (createIndex.find()) {
                String indexName = createIndex.group(1);
                String tableName = createIndex.group(2);
                if (!indexExists(connection, tableName, indexName)) {
                    execute(connection, sql);
                }
            } else if (uniqueConstraint.find()) {
                String tableName = uniqueConstraint.group(1);
                String constraintName = uniqueConstraint.group(2);
                if (!constraintExists(connection, tableName, constraintName)
                        && !indexExists(connection, tableName, constraintName)) {
                    execute(connection, sql);
                }
            } else if (foreignKey.find()) {
                String tableName = foreignKey.group(1);
                String constraintName = foreignKey.group(2);
                if (!constraintExists(connection, tableName, constraintName)
                        && !foreignKeyExists(connection, tableName, constraintName)) {
                    execute(connection, sql);
                }
            } else {
                throw new IllegalStateException(
                        "Unsupported PMS baseline statement: " + sql
                );
            }
        }

        List<String> missingTables = expectedTables.stream()
                .filter(tableName -> !tableExistsUnchecked(connection, tableName))
                .toList();
        if (!missingTables.isEmpty()) {
            throw new IllegalStateException(
                    "PMS schema installation is incomplete. Missing tables: "
                            + String.join(", ", missingTables)
            );
        }
    }

    private List<String> loadPmsStatements() throws IOException {
        try (var input = getClass().getResourceAsStream(BASELINE_RESOURCE)) {
            Objects.requireNonNull(
                    input,
                    "Missing schema baseline resource " + BASELINE_RESOURCE
            );
            String baseline = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            return baseline.lines()
                    .map(String::trim)
                    .filter(this::isPmsStatement)
                    .map(V15__install_pms_schema_on_legacy_baseline::withoutSemicolon)
                    .toList();
        }
    }

    private boolean isPmsStatement(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        return normalized.startsWith("create table pms_")
                || normalized.matches("^create index .+ on pms_.+")
                || normalized.startsWith("alter table pms_");
    }

    private static String withoutSemicolon(String sql) {
        return sql.endsWith(";")
                ? sql.substring(0, sql.length() - 1)
                : sql;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean indexExists(
            Connection connection,
            String tableName,
            String indexName
    ) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(
                connection.getCatalog(),
                null,
                tableName,
                false,
                false
        )) {
            while (indexes.next()) {
                if (equalsIgnoreCase(indexName, indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean foreignKeyExists(
            Connection connection,
            String tableName,
            String constraintName
    ) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet keys = metadata.getImportedKeys(
                connection.getCatalog(),
                null,
                tableName
        )) {
            while (keys.next()) {
                if (equalsIgnoreCase(constraintName, keys.getString("FK_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean constraintExists(
            Connection connection,
            String tableName,
            String constraintName
    ) throws SQLException {
        String sql = """
                select 1
                from information_schema.table_constraints
                where lower(table_schema) = lower(?)
                  and lower(table_name) = lower(?)
                  and lower(constraint_name) = lower(?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currentSchema(connection));
            statement.setString(2, tableName);
            statement.setString(3, constraintName);
            try (ResultSet constraints = statement.executeQuery()) {
                return constraints.next();
            }
        }
    }

    private boolean tableExistsUnchecked(
            Connection connection,
            String tableName
    ) {
        try {
            return tableExists(connection, tableName);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not verify PMS table " + tableName,
                    exception
            );
        }
    }

    private boolean tableExists(
            Connection connection,
            String tableName
    ) throws SQLException {
        String sql = """
                select 1
                from information_schema.tables
                where lower(table_schema) = lower(?)
                  and lower(table_name) = lower(?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currentSchema(connection));
            statement.setString(2, tableName);
            try (ResultSet tables = statement.executeQuery()) {
                return tables.next();
            }
        }
    }

    private String currentSchema(Connection connection) throws SQLException {
        String productName = connection.getMetaData()
                .getDatabaseProductName()
                .toLowerCase(Locale.ROOT);
        if (productName.contains("mysql") || productName.contains("mariadb")) {
            return connection.getCatalog();
        }
        return connection.getSchema();
    }

    private boolean equalsIgnoreCase(String expected, String actual) {
        return actual != null && expected.equalsIgnoreCase(actual);
    }
}
