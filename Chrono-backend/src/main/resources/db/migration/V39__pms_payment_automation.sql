ALTER TABLE pms_payments ADD COLUMN merchant_context VARCHAR(180) NULL;
ALTER TABLE pms_payments ADD COLUMN provider_checked_at DATETIME NULL;
ALTER TABLE pms_payment_requests ADD COLUMN merchant_context VARCHAR(180) NULL;
ALTER TABLE pms_payment_requests ADD COLUMN automation_error VARCHAR(500) NULL;
ALTER TABLE pms_payment_requests ADD COLUMN last_checked_at DATETIME NULL;
CREATE TABLE pms_payment_settings (
    property_id BIGINT NOT NULL PRIMARY KEY,
    merchant_context VARCHAR(180) NULL,
    verified_at DATETIME NULL,
    automatic_deposit_links BOOLEAN NOT NULL DEFAULT FALSE,
    deposit_cursor BIGINT NOT NULL DEFAULT 0,
    last_automation_at DATETIME NULL,
    last_automation_error VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_settings_property FOREIGN KEY (property_id) REFERENCES pms_properties(id)
);
CREATE TABLE pms_stripe_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(180) NOT NULL,
    account_id VARCHAR(180) NULL,
    event_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(180) NOT NULL,
    request_key VARCHAR(80) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    received_at DATETIME NOT NULL,
    last_error VARCHAR(500) NULL,
    CONSTRAINT uk_pms_stripe_event UNIQUE(event_id)
);
CREATE INDEX idx_pms_stripe_event_due ON pms_stripe_events(status,next_attempt_at);
CREATE INDEX idx_pms_payment_request_status_checked ON pms_payment_requests(status,last_checked_at);
