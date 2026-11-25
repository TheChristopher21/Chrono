package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CorrectionOriginalTimeMigrationTest {
    @Test
    void preservesLegacyOriginalsAndAllowsReplacingTheLiveStamps() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:correction_v50;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("create table time_tracking_entries (id bigint primary key, entry_timestamp datetime(6), punch_type enum ('ENDE','START'))");
            statement.execute("create table correction_requests (id bigint primary key, target_entry_id bigint references time_tracking_entries(id))");
            statement.execute("insert into time_tracking_entries values (51, '2026-10-06 17:00:00', 'ENDE')");
            statement.execute("insert into correction_requests values (1, 51), (2, null)");

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V50__correction_original_time_snapshots.sql"));
            statement.execute("delete from time_tracking_entries where id = 51");
            try (var result = statement.executeQuery("select * from correction_requests where id = 1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getTimestamp("original_timestamp").toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 6, 17, 0));
                assertThat(result.getString("original_punch_type")).isEqualTo("ENDE");
                assertThat(result.getLong("original_entry_id")).isEqualTo(51L);
                assertThat(result.getObject("target_entry_id")).isNull();
                assertThat(result.getBoolean("original_day_captured")).isFalse();
            }
            try (var result = statement.executeQuery("select original_timestamp from correction_requests where id = 2")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isNull();
            }
            statement.execute("insert into correction_original_entries values (1, 51, '2026-10-06 17:00:00', 'ENDE')");
            statement.execute("delete from correction_requests where id = 1");
            try (var result = statement.executeQuery("select count(*) from correction_original_entries")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isZero();
            }
        }
    }
}
