CREATE TABLE pms_public_booking_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pms_public_booking_request UNIQUE (property_id, idempotency_key),
    CONSTRAINT fk_pms_public_booking_request_property
        FOREIGN KEY (property_id) REFERENCES pms_properties (id),
    CONSTRAINT fk_pms_public_booking_request_reservation
        FOREIGN KEY (reservation_id) REFERENCES pms_reservations (id)
) ENGINE=InnoDB;

CREATE INDEX idx_pms_public_booking_request_created
    ON pms_public_booking_requests (created_at);

CREATE TABLE pms_public_rate_limits (
    rate_key CHAR(64) NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    request_count INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (rate_key)
) ENGINE=InnoDB;

CREATE INDEX idx_pms_public_rate_limit_updated
    ON pms_public_rate_limits (updated_at);

CREATE INDEX idx_pms_folio_property_status_created
    ON pms_folios (status, created_at);

CREATE INDEX idx_pms_maintenance_property_status_reported
    ON pms_maintenance_work_orders (property_id, status, reported_at);

ALTER TABLE pms_payments
    ADD COLUMN provider_transaction_id VARCHAR(160) NULL;

CREATE UNIQUE INDEX uk_pms_payment_provider_transaction
    ON pms_payments (provider_transaction_id);

ALTER TABLE pms_audit_events
    ADD COLUMN sequence_number BIGINT NULL;

ALTER TABLE pms_audit_events
    ADD COLUMN previous_hash CHAR(64) NULL;

ALTER TABLE pms_audit_events
    ADD COLUMN signature_version INT NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX uk_pms_audit_property_sequence
    ON pms_audit_events (property_id, sequence_number);

CREATE TABLE pms_webhook_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    connection_id BIGINT NOT NULL,
    delivery_id VARCHAR(100) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pms_webhook_delivery UNIQUE (connection_id, delivery_id),
    CONSTRAINT fk_pms_webhook_delivery_connection
        FOREIGN KEY (connection_id) REFERENCES pms_channel_connections (id)
) ENGINE=InnoDB;

CREATE INDEX idx_pms_webhook_delivery_received
    ON pms_webhook_deliveries (received_at);

ALTER TABLE pms_channel_connections
    ADD COLUMN webhook_key VARCHAR(64) NULL;

UPDATE pms_channel_connections
SET webhook_key = CONCAT('legacy-', id)
WHERE webhook_key IS NULL;

CREATE UNIQUE INDEX uk_pms_channel_webhook_key
    ON pms_channel_connections (webhook_key);

CREATE TABLE pms_public_booking_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_request_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pms_booking_verification_request UNIQUE (booking_request_id),
    CONSTRAINT uk_pms_booking_verification_token UNIQUE (token_hash),
    CONSTRAINT fk_pms_booking_verification_request
        FOREIGN KEY (booking_request_id) REFERENCES pms_public_booking_requests (id)
) ENGINE=InnoDB;

CREATE INDEX idx_pms_booking_verification_expiry
    ON pms_public_booking_verifications (expires_at);
