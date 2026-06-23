SET @old_schedule_entry_unique_index := (
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
    LIMIT 1
);

SET @drop_old_schedule_entry_unique_index_sql := IF(
    @old_schedule_entry_unique_index IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE schedule_entries DROP INDEX `', @old_schedule_entry_unique_index, '`')
);

PREPARE drop_old_schedule_entry_unique_index_stmt FROM @drop_old_schedule_entry_unique_index_sql;
EXECUTE drop_old_schedule_entry_unique_index_stmt;
DEALLOCATE PREPARE drop_old_schedule_entry_unique_index_stmt;

SET @new_schedule_entry_unique_index := (
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
    LIMIT 1
);

SET @create_schedule_entry_unique_index_sql := IF(
    @new_schedule_entry_unique_index IS NULL,
    'CREATE UNIQUE INDEX uk_schedule_entries_user_date_shift ON schedule_entries (user_id, `date`, shift)',
    'SELECT 1'
);

PREPARE create_schedule_entry_unique_index_stmt FROM @create_schedule_entry_unique_index_sql;
EXECUTE create_schedule_entry_unique_index_stmt;
DEALLOCATE PREPARE create_schedule_entry_unique_index_stmt;
