package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserUiPreferenceMigrationTest {

    @Test
    void createsScopedSchemaWithOptimisticRevisionAndCascadeCleanup() throws Exception {
        String url = "jdbc:h2:mem:user_ui_preferences_v20;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("create table companies (id bigint primary key)");
            statement.execute("create table users (id bigint primary key)");
            statement.execute("create table pms_properties (id bigint primary key)");
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V20__user_ui_preferences.sql")
            );

            assertThat(columnExists(connection, "user_ui_preferences", "schema_version")).isTrue();
            assertThat(columnExists(connection, "user_ui_preferences", "revision")).isTrue();
            assertThat(foreignKeyDeleteRule(connection, "fk_ui_pref_user"))
                    .isEqualTo((short) DatabaseMetaData.importedKeyCascade);
            assertThat(foreignKeyDeleteRule(connection, "fk_ui_pref_company"))
                    .isEqualTo((short) DatabaseMetaData.importedKeyCascade);
            assertThat(foreignKeyDeleteRule(connection, "fk_ui_pref_property"))
                    .isEqualTo((short) DatabaseMetaData.importedKeyCascade);

            statement.execute("insert into companies (id) values (1)");
            statement.execute("insert into users (id) values (1)");
            statement.execute("insert into pms_properties (id) values (1)");
            statement.execute("""
                    insert into user_ui_preferences (
                        user_id, company_id, property_id, tenant_key, area, context_key,
                        schema_version, payload, created_at, updated_at
                    ) values (
                        1, 1, 1, 'company:1', 'PMS_DASHBOARD', 'property:1',
                        1, '{}', current_timestamp, current_timestamp
                    )
                    """);

            assertThat(singleLong(statement, "select revision from user_ui_preferences"))
                    .isZero();
            assertThatThrownBy(() -> statement.execute("""
                    insert into user_ui_preferences (
                        user_id, company_id, property_id, tenant_key, area, context_key,
                        schema_version, payload, created_at, updated_at
                    ) values (
                        1, 1, 1, 'company:1', 'PMS_DASHBOARD', 'property:1',
                        1, '{}', current_timestamp, current_timestamp
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            statement.execute("delete from pms_properties where id = 1");
            assertThat(singleLong(statement, "select count(*) from user_ui_preferences"))
                    .isZero();

            statement.execute("""
                    insert into user_ui_preferences (
                        user_id, company_id, property_id, tenant_key, area, context_key,
                        schema_version, payload, created_at, updated_at
                    ) values (
                        1, 1, null, 'company:1', 'APP_TABS', 'workspace',
                        1, '{"tabs":[]}', current_timestamp, current_timestamp
                    )
                    """);
            statement.execute("delete from users where id = 1");
            assertThat(singleLong(statement, "select count(*) from user_ui_preferences"))
                    .isZero();
        }
    }

    private boolean columnExists(
            java.sql.Connection connection,
            String tableName,
            String columnName
    ) throws Exception {
        try (var result = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return result.next();
        }
    }

    private short foreignKeyDeleteRule(java.sql.Connection connection, String foreignKeyName)
            throws Exception {
        try (var result = connection.getMetaData().getImportedKeys(
                null,
                null,
                "user_ui_preferences"
        )) {
            while (result.next()) {
                if (foreignKeyName.equalsIgnoreCase(result.getString("FK_NAME"))) {
                    return result.getShort("DELETE_RULE");
                }
            }
        }
        throw new AssertionError("Missing foreign key " + foreignKeyName);
    }

    private long singleLong(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}
