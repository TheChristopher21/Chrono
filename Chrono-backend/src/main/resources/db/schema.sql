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
ALTER TABLE companies ADD COLUMN IF NOT EXISTS custom_holiday_selection_enabled BOOLEAN DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS company_holiday_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    holiday_code VARCHAR(80) NOT NULL,
    half_day BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_company_holiday_preferences_company_code UNIQUE (company_id, holiday_code),
    CONSTRAINT fk_company_holiday_preferences_company FOREIGN KEY (company_id) REFERENCES companies(id)
);
INSERT INTO company_holiday_preferences (company_id, holiday_code, half_day)
SELECT c.id, sg_holidays.holiday_code, FALSE
FROM companies c
JOIN (
    SELECT 'CH_NEUJAHR' AS holiday_code
    UNION ALL SELECT 'CH_BERCHTOLDSTAG'
    UNION ALL SELECT 'CH_KARFREITAG'
    UNION ALL SELECT 'CH_OSTERMONTAG'
    UNION ALL SELECT 'CH_TAG_DER_ARBEIT'
    UNION ALL SELECT 'CH_AUFFAHRT'
    UNION ALL SELECT 'CH_PFINGSTMONTAG'
    UNION ALL SELECT 'CH_NATIONALFEIERTAG'
    UNION ALL SELECT 'CH_ALLERHEILIGEN'
    UNION ALL SELECT 'CH_WEIHNACHTEN'
    UNION ALL SELECT 'CH_STEPHANSTAG'
) sg_holidays
WHERE NOT EXISTS (
    SELECT 1
    FROM company_holiday_preferences existing
    WHERE existing.company_id = c.id
      AND existing.holiday_code = sg_holidays.holiday_code
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_customer_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_last_customer FOREIGN KEY (last_customer_id) REFERENCES customers(id);
ALTER TABLE users ADD COLUMN IF NOT EXISTS include_in_time_tracking BOOLEAN DEFAULT TRUE;
-- Regular users should always remain visible in time tracking views. If an installation
-- accidentally stored include_in_time_tracking = FALSE for non-admin users, correct it.
UPDATE users SET include_in_time_tracking = TRUE
WHERE include_in_time_tracking = FALSE
  AND id NOT IN (
    SELECT ur.user_id
    FROM user_roles ur
    INNER JOIN roles r ON r.id = ur.role_id
    WHERE r.role_name IN ('ROLE_ADMIN', 'ROLE_SUPERADMIN')
);
UPDATE users SET include_in_time_tracking = FALSE
WHERE id IN (
  SELECT ur.user_id
  FROM user_roles ur
  INNER JOIN roles r ON r.id = ur.role_id
  WHERE r.role_name = 'ROLE_SUPERADMIN'
);
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
