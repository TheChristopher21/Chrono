CREATE TABLE pms_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    actor VARCHAR(120) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    details VARCHAR(4000) NOT NULL,
    integrity_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_pms_audit_property_created (property_id, created_at),
    INDEX idx_pms_audit_company_created (company_id, created_at),
    CONSTRAINT fk_pms_audit_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_pms_audit_property FOREIGN KEY (property_id) REFERENCES pms_properties(id)
);

ALTER TABLE pms_integration_outbox
    ADD COLUMN next_attempt_at DATETIME(6) NULL,
    ADD COLUMN last_attempt_at DATETIME(6) NULL,
    ADD COLUMN last_error VARCHAR(1000) NULL,
    ADD COLUMN locked_at DATETIME(6) NULL,
    ADD COLUMN lock_owner VARCHAR(120) NULL;

UPDATE pms_integration_outbox
SET next_attempt_at = created_at
WHERE status IN ('PENDING', 'FAILED')
  AND next_attempt_at IS NULL;

ALTER TABLE pms_integration_outbox
    DROP INDEX idx_pms_outbox_property_status,
    ADD INDEX idx_pms_outbox_property_status (
        property_id, status, next_attempt_at, created_at
    );
