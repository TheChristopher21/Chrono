-- Baseline schemas used a closed ENUM before durable refund intents were introduced.
ALTER TABLE pms_payments MODIFY COLUMN status VARCHAR(24) NOT NULL;
ALTER TABLE pms_payments MODIFY COLUMN method VARCHAR(24) NOT NULL;
