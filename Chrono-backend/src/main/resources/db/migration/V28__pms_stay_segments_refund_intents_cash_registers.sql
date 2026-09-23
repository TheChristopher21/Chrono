ALTER TABLE pms_cash_shifts ADD COLUMN register_code VARCHAR(32) NOT NULL DEFAULT 'FRONTDESK';
ALTER TABLE pms_cash_shifts ADD COLUMN outlet_code VARCHAR(32) NOT NULL DEFAULT 'FRONTDESK';
CREATE INDEX idx_pms_cash_register_status ON pms_cash_shifts(property_id, register_code, status);
ALTER TABLE pms_payments ADD COLUMN cash_shift_id BIGINT;
ALTER TABLE pms_payments ADD COLUMN provider_status VARCHAR(40);
ALTER TABLE pms_payments ADD COLUMN refund_request_id VARCHAR(80);
ALTER TABLE pms_payments ADD COLUMN posting_date DATE;
ALTER TABLE pms_payments ADD CONSTRAINT fk_pms_payment_cash_shift FOREIGN KEY (cash_shift_id) REFERENCES pms_cash_shifts(id);
CREATE UNIQUE INDEX uk_pms_payment_refund_request ON pms_payments(refund_request_id);
UPDATE pms_payments SET posting_date = CAST(received_at AS DATE) WHERE posting_date IS NULL;

CREATE TABLE pms_reservation_room_segments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    rate_plan_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(500),
    created_by VARCHAR(120) NOT NULL,
    CONSTRAINT fk_pms_segment_reservation FOREIGN KEY (reservation_id) REFERENCES pms_reservations(id),
    CONSTRAINT fk_pms_segment_room FOREIGN KEY (room_id) REFERENCES pms_rooms(id),
    CONSTRAINT fk_pms_segment_rate FOREIGN KEY (rate_plan_id) REFERENCES pms_rate_plans(id)
);
CREATE INDEX idx_pms_segment_room_dates ON pms_reservation_room_segments(room_id, start_date, end_date);
CREATE INDEX idx_pms_segment_reservation ON pms_reservation_room_segments(reservation_id);

CREATE TABLE pms_reservation_guests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    guest_id BIGINT NOT NULL,
    arrival_date DATE NOT NULL,
    departure_date DATE NOT NULL,
    is_child BOOLEAN NOT NULL DEFAULT FALSE,
    address_line VARCHAR(180),
    postal_code VARCHAR(20),
    city VARCHAR(120),
    country_code VARCHAR(2),
    nationality_code VARCHAR(2),
    document_hash VARCHAR(64),
    document_last_four VARCHAR(4),
    signature_name VARCHAR(180),
    registration_completed_at TIMESTAMP NULL,
    CONSTRAINT fk_pms_coguest_reservation FOREIGN KEY (reservation_id) REFERENCES pms_reservations(id),
    CONSTRAINT fk_pms_coguest_profile FOREIGN KEY (guest_id) REFERENCES pms_guests(id),
    CONSTRAINT uk_pms_reservation_guest UNIQUE (reservation_id, guest_id)
);
