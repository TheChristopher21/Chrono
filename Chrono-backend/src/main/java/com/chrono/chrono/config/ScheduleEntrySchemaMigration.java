package com.chrono.chrono.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScheduleEntrySchemaMigration implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleEntrySchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public ScheduleEntrySchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> oldIndexes = jdbcTemplate.queryForList("""
                    SELECT index_name
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'schedule_entries'
                      AND non_unique = 0
                      AND index_name <> 'PRIMARY'
                    GROUP BY index_name
                    HAVING SUM(CASE WHEN column_name = 'user_id' THEN 1 ELSE 0 END) = 1
                       AND SUM(CASE WHEN column_name = 'date' THEN 1 ELSE 0 END) = 1
                       AND SUM(CASE WHEN column_name = 'shift' THEN 1 ELSE 0 END) = 0
                       AND COUNT(DISTINCT column_name) = 2
                    """, String.class);

            for (String indexName : oldIndexes) {
                jdbcTemplate.execute("ALTER TABLE schedule_entries DROP INDEX " + quoteIdentifier(indexName));
                logger.info("Dropped legacy schedule_entries unique index {}", indexName);
            }

            Integer newIndexCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM (
                        SELECT index_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'schedule_entries'
                          AND non_unique = 0
                          AND index_name <> 'PRIMARY'
                        GROUP BY index_name
                        HAVING SUM(CASE WHEN column_name = 'user_id' THEN 1 ELSE 0 END) = 1
                           AND SUM(CASE WHEN column_name = 'date' THEN 1 ELSE 0 END) = 1
                           AND SUM(CASE WHEN column_name = 'shift' THEN 1 ELSE 0 END) = 1
                           AND COUNT(DISTINCT column_name) = 3
                    ) matching_indexes
                    """, Integer.class);

            if (newIndexCount == null || newIndexCount == 0) {
                jdbcTemplate.execute("CREATE UNIQUE INDEX uk_schedule_entries_user_date_shift ON schedule_entries (user_id, `date`, shift)");
                logger.info("Created schedule_entries unique index for user, date and shift");
            }
        } catch (Exception ex) {
            logger.warn("Could not verify schedule_entries unique index migration. Continuing startup.", ex);
        }
    }

    private String quoteIdentifier(String identifier) {
        return "`" + String.valueOf(identifier).replace("`", "``") + "`";
    }
}
