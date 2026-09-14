CREATE TABLE pms_approval_policies (
 property_id BIGINT NOT NULL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 enabled BOOLEAN NOT NULL DEFAULT FALSE, refund_threshold DECIMAL(19,4) NOT NULL DEFAULT 500,
 CONSTRAINT fk_pms_approval_policy_property FOREIGN KEY(property_id) REFERENCES pms_properties(id)
);
CREATE TABLE pms_financial_approvals (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, property_id BIGINT NOT NULL,
 request_key VARCHAR(80) NOT NULL, payment_id BIGINT NOT NULL, amount DECIMAL(19,4) NOT NULL, currency VARCHAR(3) NOT NULL,
 reason VARCHAR(500) NOT NULL, cash_shift_id BIGINT NULL, request_hash VARCHAR(64) NOT NULL,
 requested_by VARCHAR(120) NOT NULL, status VARCHAR(20) NOT NULL, decided_by VARCHAR(120), decision_reason VARCHAR(500),
 created_at DATETIME NOT NULL, expires_at DATETIME NOT NULL, decided_at DATETIME, consumed_at DATETIME,
 CONSTRAINT fk_pms_approval_property FOREIGN KEY(property_id) REFERENCES pms_properties(id),
 CONSTRAINT fk_pms_approval_payment FOREIGN KEY(payment_id) REFERENCES pms_payments(id),
 CONSTRAINT ux_pms_approval_request UNIQUE(property_id,request_key),
 INDEX idx_pms_approval_status(property_id,status,created_at)
);
