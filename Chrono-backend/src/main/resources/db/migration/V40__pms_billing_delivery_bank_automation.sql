ALTER TABLE pms_invoices ADD COLUMN language_code VARCHAR(5) NOT NULL DEFAULT 'DE';

CREATE TABLE pms_billing_settings (
 property_id BIGINT NOT NULL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 mail_enabled BOOLEAN NOT NULL DEFAULT FALSE, sender_email VARCHAR(190), sender_name VARCHAR(120), reply_to VARCHAR(190),
 automatic_invoices BOOLEAN NOT NULL DEFAULT FALSE, invoice_language VARCHAR(5) NOT NULL DEFAULT 'DE',
 automatic_reminders BOOLEAN NOT NULL DEFAULT FALSE, send_reminders BOOLEAN NOT NULL DEFAULT FALSE,
 first_reminder_days INT NOT NULL DEFAULT 7, reminder_interval_days INT NOT NULL DEFAULT 14, max_reminders INT NOT NULL DEFAULT 3,
 invoice_subject VARCHAR(240), invoice_body TEXT, reminder_subject VARCHAR(240), reminder_body TEXT,
 updated_at DATETIME(6), updated_by VARCHAR(120),
 CONSTRAINT fk_pms_billing_property FOREIGN KEY(property_id) REFERENCES pms_properties(id)
);
CREATE TABLE pms_invoice_documents (
 invoice_id BIGINT NOT NULL PRIMARY KEY, content LONGBLOB NOT NULL, sha256 VARCHAR(64) NOT NULL, created_at DATETIME(6),
 CONSTRAINT fk_pms_document_invoice FOREIGN KEY(invoice_id) REFERENCES pms_invoices(id)
);
CREATE TABLE pms_delivery_jobs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, property_id BIGINT NOT NULL, invoice_id BIGINT, communication_id BIGINT, receivable_id BIGINT,
 outstanding_at_queue DECIMAL(19,4), request_key VARCHAR(160) NOT NULL, kind VARCHAR(32) NOT NULL,
 recipient VARCHAR(190) NOT NULL, sender_email VARCHAR(190) NOT NULL, sender_name VARCHAR(120), reply_to VARCHAR(190),
 subject VARCHAR(240) NOT NULL, body TEXT NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
 message_id VARCHAR(190) NOT NULL, payload_hash VARCHAR(64) NOT NULL, attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NOT NULL, lease_until DATETIME(6), lease_owner VARCHAR(80), last_error VARCHAR(1000),
 created_at DATETIME(6), created_by VARCHAR(120), sent_at DATETIME(6),
 CONSTRAINT uk_pms_delivery_key UNIQUE(property_id,request_key),
 CONSTRAINT fk_pms_delivery_property FOREIGN KEY(property_id) REFERENCES pms_properties(id),
 CONSTRAINT fk_pms_delivery_invoice FOREIGN KEY(invoice_id) REFERENCES pms_invoices(id),
 CONSTRAINT fk_pms_delivery_communication FOREIGN KEY(communication_id) REFERENCES pms_guest_communications(id),
 CONSTRAINT fk_pms_delivery_receivable FOREIGN KEY(receivable_id) REFERENCES pms_receivables(id)
);
CREATE INDEX idx_pms_delivery_due ON pms_delivery_jobs(status,next_attempt_at);
CREATE TABLE pms_delivery_attachments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, job_id BIGINT NOT NULL,
 filename VARCHAR(180) NOT NULL, content_type VARCHAR(100) NOT NULL, content LONGBLOB NOT NULL, sha256 VARCHAR(64) NOT NULL,
 CONSTRAINT fk_pms_attachment_job FOREIGN KEY(job_id) REFERENCES pms_delivery_jobs(id)
);
CREATE TABLE pms_dunning_notices (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, property_id BIGINT NOT NULL, receivable_id BIGINT NOT NULL, invoice_id BIGINT NOT NULL, level_number INT NOT NULL,
 business_date DATE, due_date DATE, outstanding_amount DECIMAL(19,4) NOT NULL, currency_code VARCHAR(3) NOT NULL,
 delivery_id BIGINT, created_at DATETIME(6), created_by VARCHAR(120),
 CONSTRAINT uk_pms_dunning_level UNIQUE(receivable_id,level_number),
 CONSTRAINT fk_pms_dunning_property FOREIGN KEY(property_id) REFERENCES pms_properties(id),
 CONSTRAINT fk_pms_dunning_receivable FOREIGN KEY(receivable_id) REFERENCES pms_receivables(id),
 CONSTRAINT fk_pms_dunning_invoice FOREIGN KEY(invoice_id) REFERENCES pms_invoices(id),
 CONSTRAINT fk_pms_dunning_delivery FOREIGN KEY(delivery_id) REFERENCES pms_delivery_jobs(id)
);
CREATE TABLE pms_bank_imports (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, property_id BIGINT NOT NULL, filename VARCHAR(180) NOT NULL,
 file_hash VARCHAR(64) NOT NULL, row_count INT NOT NULL, created_at DATETIME(6), created_by VARCHAR(120),
 CONSTRAINT uk_pms_bank_import_hash UNIQUE(property_id,file_hash),
 CONSTRAINT fk_pms_bank_import_property FOREIGN KEY(property_id) REFERENCES pms_properties(id)
);
CREATE TABLE pms_bank_transactions (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, property_id BIGINT NOT NULL, import_id BIGINT NOT NULL,
 bank_account VARCHAR(100) NOT NULL, external_id VARCHAR(160) NOT NULL, booking_date DATE, currency_code VARCHAR(3) NOT NULL,
 amount DECIMAL(19,4) NOT NULL, reference VARCHAR(500), debtor_name VARCHAR(180), status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 receivable_id BIGINT, matched_at DATETIME(6), matched_by VARCHAR(120),
 CONSTRAINT uk_pms_bank_transaction UNIQUE(property_id,bank_account,external_id),
 CONSTRAINT fk_pms_bank_transaction_property FOREIGN KEY(property_id) REFERENCES pms_properties(id),
 CONSTRAINT fk_pms_bank_transaction_import FOREIGN KEY(import_id) REFERENCES pms_bank_imports(id),
 CONSTRAINT fk_pms_bank_transaction_receivable FOREIGN KEY(receivable_id) REFERENCES pms_receivables(id)
);
CREATE TABLE pms_accounting_export_runs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, property_id BIGINT NOT NULL, request_key VARCHAR(120) NOT NULL,
 from_date DATE, to_exclusive DATE, status VARCHAR(24) NOT NULL DEFAULT 'GENERATED', content LONGBLOB NOT NULL,
 sha256 VARCHAR(64) NOT NULL, created_at DATETIME(6), created_by VARCHAR(120),
 acknowledged_at DATETIME(6), acknowledged_by VARCHAR(120), acknowledgement_reference VARCHAR(190),
 CONSTRAINT uk_pms_export_request UNIQUE(property_id,request_key),
 CONSTRAINT fk_pms_export_run_property FOREIGN KEY(property_id) REFERENCES pms_properties(id)
);
