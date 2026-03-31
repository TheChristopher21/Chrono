CREATE TABLE IF NOT EXISTS user_employment_model_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    model_type VARCHAR(20) NOT NULL,
    effective_from DATE NOT NULL,
    work_percentage INT NULL,
    expected_work_days INT NULL,
    daily_work_hours DOUBLE NULL,
    schedule_cycle INT NULL,
    schedule_effective_date DATE NULL,
    weekly_schedule TEXT NULL,
    CONSTRAINT fk_user_employment_model_history_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_user_employment_model_history_user_effective
    ON user_employment_model_history (user_id, effective_from);

CREATE UNIQUE INDEX uq_user_employment_model_history_user_effective
    ON user_employment_model_history (user_id, effective_from);
