CREATE TABLE pms_operational_alert_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_id BIGINT NOT NULL,
    alert_code VARCHAR(100) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    destination_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    severity INTEGER NOT NULL DEFAULT 0,
    notified_severity INTEGER NOT NULL DEFAULT 0,
    observed_at DATETIME(6) NOT NULL,
    claim_token VARCHAR(36),
    claim_until DATETIME(6),
    notified_at DATETIME(6),
    CONSTRAINT uk_pms_alert_delivery UNIQUE (property_id, alert_code, channel),
    CONSTRAINT fk_pms_alert_delivery_property FOREIGN KEY (property_id) REFERENCES pms_properties(id)
);
