CREATE TABLE pms_payment_requests (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, folio_id BIGINT NOT NULL,
 request_key VARCHAR(80) NOT NULL,
 kind VARCHAR(20) NOT NULL, status VARCHAR(32) NOT NULL, amount NUMERIC(12,2) NOT NULL,
 capture_amount NUMERIC(12,2), currency_code VARCHAR(3) NOT NULL,
 provider_session_id VARCHAR(160) UNIQUE, provider_payment_intent_id VARCHAR(160),
 checkout_url VARCHAR(3000), provider_status VARCHAR(64), action_requested VARCHAR(20),
 created_by VARCHAR(120) NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 CONSTRAINT uk_pms_payment_request_key UNIQUE(request_key),
 CONSTRAINT fk_pms_payment_request_folio FOREIGN KEY(folio_id) REFERENCES pms_folios(id)
);
CREATE INDEX idx_pms_payment_request_folio ON pms_payment_requests(folio_id);
ALTER TABLE pms_rate_plans ADD COLUMN no_show_fee_percent NUMERIC(7,4);
ALTER TABLE pms_rate_plans ADD COLUMN policy_fee_tax_rate NUMERIC(7,4);
ALTER TABLE pms_rate_plans ADD COLUMN deposit_due_days_before_arrival INTEGER;
ALTER TABLE pms_reservations ADD COLUMN policy_snapshot_at DATETIME;
ALTER TABLE pms_reservations ADD COLUMN policy_cancellation_deadline_hours INTEGER;
ALTER TABLE pms_reservations ADD COLUMN policy_cancellation_fee_percent NUMERIC(7,4);
ALTER TABLE pms_reservations ADD COLUMN policy_no_show_fee_percent NUMERIC(7,4);
ALTER TABLE pms_reservations ADD COLUMN policy_fee_tax_rate NUMERIC(7,4);
ALTER TABLE pms_reservations ADD COLUMN policy_deposit_percent NUMERIC(7,4);
ALTER TABLE pms_reservations ADD COLUMN policy_deposit_due_days_before_arrival INTEGER;
ALTER TABLE pms_reservations ADD COLUMN policy_check_in_time TIME;
