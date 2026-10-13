ALTER TABLE bank_payment_batches
    ADD COLUMN IF NOT EXISTS delivery_channel VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_artifact_path VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS provider_artifact_name VARCHAR(255);
