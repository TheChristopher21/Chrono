package com.chrono.chrono.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CompanyHolidayMigrationTest {

    @Test
    void v9SeedsExistingCompaniesWithStGallenHolidayPreferencesWithoutEnablingCustomMode() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:company_holiday_migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        )) {
            execute(connection, "CREATE TABLE companies (id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))");
            execute(connection, "INSERT INTO companies (name) VALUES ('Chrono AG'), ('Bestehende GmbH')");

            executeScript(connection, Path.of("src/main/resources/db/migration/V9__company_holiday_preferences.sql"));

            assertEquals(22, count(connection, "SELECT COUNT(*) FROM company_holiday_preferences"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM company_holiday_preferences WHERE holiday_code = 'CH_BERCHTOLDSTAG'"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM company_holiday_preferences WHERE holiday_code = 'CH_ALLERHEILIGEN'"));

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT custom_holiday_selection_enabled FROM companies WHERE id = 1")) {
                resultSet.next();
                assertFalse(resultSet.getBoolean(1));
            }
        }
    }

    private static void executeScript(Connection connection, Path scriptPath) throws Exception {
        String script = Files.readString(scriptPath);
        for (String statement : script.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                execute(connection, trimmed);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
