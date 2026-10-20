ALTER TABLE pms_housekeeping_tasks ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pms_housekeeping_tasks ADD COLUMN work_type VARCHAR(24) NOT NULL DEFAULT 'CLEAN';
ALTER TABLE pms_housekeeping_tasks ADD COLUMN work_status VARCHAR(24) NOT NULL DEFAULT 'OPEN';
UPDATE pms_housekeeping_tasks SET work_type='INSPECTION' WHERE type='INSPECTION';
UPDATE pms_housekeeping_tasks SET work_status=CASE WHEN status='CLEAN' THEN 'DONE' WHEN status='IN_PROGRESS' THEN 'IN_PROGRESS' ELSE 'OPEN' END;
ALTER TABLE pms_housekeeping_tasks ADD CONSTRAINT uk_pms_housekeeping_room_date_work UNIQUE(room_id, service_date, work_type);
ALTER TABLE pms_housekeeping_tasks DROP INDEX uk_pms_housekeeping_room_date;
CREATE TABLE pms_housekeeping_task_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    assigned_to VARCHAR(120),
    notes VARCHAR(1000),
    actor VARCHAR(120) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_pms_hk_event_task FOREIGN KEY(task_id) REFERENCES pms_housekeeping_tasks(id),
    INDEX idx_pms_hk_events_task(task_id,id)
);
INSERT INTO pms_housekeeping_task_events(task_id,to_status,assigned_to,notes,actor,created_at)
SELECT id,work_status,assigned_to,'Übernahme vorhandener Housekeeping-Aufgabe','MIGRATION',CURRENT_TIMESTAMP FROM pms_housekeeping_tasks;
