package com.chrono.chrono.config;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The pre-V50 columns already present in the B14 Chrono schema. */
final class LegacyCorrectionMigrationFixture {
    private LegacyCorrectionMigrationFixture() {}

    static void create(Statement statement) throws Exception {
        statement.execute("""
                create table time_tracking_entries (
                    id bigint primary key,
                    entry_timestamp datetime(6) not null,
                    punch_type enum ('ENDE','START') not null
                )
                """);
        statement.execute("""
                create table correction_requests (
                    id bigint primary key,
                    target_entry_id bigint,
                    constraint fk_legacy_correction_entry foreign key (target_entry_id)
                        references time_tracking_entries (id)
                )
                """);
        statement.execute("insert into time_tracking_entries values (51, '2026-10-06 17:00:00', 'ENDE')");
        statement.execute("insert into correction_requests values (1, 51), (2, null)");
    }

    static void assertOriginalsPreserved(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("select * from correction_requests where id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getTimestamp("original_timestamp").toLocalDateTime())
                        .isEqualTo(LocalDateTime.of(2026, 10, 6, 17, 0));
                assertThat(row.getString("original_punch_type")).isEqualTo("ENDE");
                assertThat(row.getLong("original_entry_id")).isEqualTo(51L);
                assertThat(row.getObject("target_entry_id")).isNull();
            }
            try (var row = statement.executeQuery("select original_timestamp from correction_requests where id = 2")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("original_timestamp")).isNull();
            }
            try (var row = statement.executeQuery("select entry_timestamp from time_tracking_entries where id = 51")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getTimestamp("entry_timestamp").toLocalDateTime())
                        .isEqualTo(LocalDateTime.of(2026, 10, 6, 17, 0));
            }
        }
    }
}
