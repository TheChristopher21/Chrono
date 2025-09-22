ALTER TABLE users ADD COLUMN IF NOT EXISTS include_in_time_tracking BOOLEAN DEFAULT TRUE;
UPDATE users SET include_in_time_tracking = TRUE WHERE include_in_time_tracking IS NULL;
