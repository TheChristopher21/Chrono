CREATE TABLE IF NOT EXISTS user (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
    );

CREATE TABLE IF NOT EXISTS time_tracking (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             user_id BIGINT,
                                             punch_in DATETIME,
                                             punch_out DATETIME,
                                             FOREIGN KEY (user_id) REFERENCES user (id)
    );

CREATE TABLE IF NOT EXISTS customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL
    );

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT,
    name VARCHAR(255) NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

ALTER TABLE time_tracking_entries ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE time_tracking_entries ADD CONSTRAINT fk_customer FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS customer_tracking_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(255);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(255);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS postal_code VARCHAR(50);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS city VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_customer_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_last_customer FOREIGN KEY (last_customer_id) REFERENCES customers(id);
ALTER TABLE time_tracking_entries ADD COLUMN IF NOT EXISTS project_id BIGINT;
ALTER TABLE time_tracking_entries ADD CONSTRAINT fk_project FOREIGN KEY (project_id) REFERENCES projects(id);

CREATE TABLE IF NOT EXISTS daily_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    note_date DATE NOT NULL,
    content VARCHAR(2000),
    CONSTRAINT fk_daily_note_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uc_daily_note UNIQUE (user_id, note_date)
);
