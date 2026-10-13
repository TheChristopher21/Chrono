ALTER TABLE bank_payment_batches
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_message VARCHAR(1024);

ALTER TABLE digital_signature_requests
    ADD COLUMN IF NOT EXISTS company_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS signing_url VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS provider_status_message VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS last_status_check DATETIME;

ALTER TABLE secure_messages
    ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_message VARCHAR(1024);

ALTER TABLE digital_signature_requests
    ADD CONSTRAINT fk_signature_request_company FOREIGN KEY (company_id) REFERENCES companies(id);
