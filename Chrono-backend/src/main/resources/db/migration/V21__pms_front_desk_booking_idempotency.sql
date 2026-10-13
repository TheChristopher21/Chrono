CREATE TABLE pms_front_desk_booking_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pms_front_desk_booking_request UNIQUE (property_id, idempotency_key),
    CONSTRAINT fk_pms_front_desk_booking_request_property
        FOREIGN KEY (property_id) REFERENCES pms_properties (id),
    CONSTRAINT fk_pms_front_desk_booking_request_reservation
        FOREIGN KEY (reservation_id) REFERENCES pms_reservations (id)
) ENGINE=InnoDB;

CREATE INDEX idx_pms_front_desk_booking_request_created
    ON pms_front_desk_booking_requests (created_at);
