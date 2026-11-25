CREATE TABLE pms_event_offers (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, resource_booking_id BIGINT NOT NULL, resource_id BIGINT NOT NULL, offer_version INT NOT NULL,
 snapshot_json MEDIUMTEXT NOT NULL, content_hash VARCHAR(64) NOT NULL, terms TEXT NOT NULL, valid_until DATETIME NOT NULL,
 created_at DATETIME NOT NULL, created_by VARCHAR(120) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 token_hash VARCHAR(64) UNIQUE, decided_at DATETIME, signature_name VARCHAR(180), decision_note VARCHAR(1000), acceptance_hash VARCHAR(64), applied_at DATETIME,
 CONSTRAINT uk_pms_event_offer_version UNIQUE(resource_booking_id,offer_version),
 CONSTRAINT fk_pms_event_offer_booking FOREIGN KEY(resource_booking_id) REFERENCES pms_resource_bookings(id)
);
