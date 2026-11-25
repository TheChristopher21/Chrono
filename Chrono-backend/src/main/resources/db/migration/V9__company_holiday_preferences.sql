ALTER TABLE companies
    ADD COLUMN custom_holiday_selection_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS company_holiday_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    holiday_code VARCHAR(80) NOT NULL,
    half_day BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_company_holiday_preferences_company_code UNIQUE (company_id, holiday_code),
    CONSTRAINT fk_company_holiday_preferences_company
        FOREIGN KEY (company_id) REFERENCES companies (id)
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
