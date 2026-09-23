ALTER TABLE pms_pos_tickets ADD COLUMN cash_shift_id BIGINT;
ALTER TABLE pms_pos_tickets ADD CONSTRAINT fk_pms_pos_cash_shift FOREIGN KEY (cash_shift_id) REFERENCES pms_cash_shifts(id);
CREATE INDEX idx_pms_pos_cash_shift ON pms_pos_tickets(cash_shift_id);
ALTER TABLE pms_pos_tickets ADD COLUMN payment_reference VARCHAR(190);
CREATE UNIQUE INDEX uk_pms_pos_payment_reference ON pms_pos_tickets(property_id, payment_reference);
