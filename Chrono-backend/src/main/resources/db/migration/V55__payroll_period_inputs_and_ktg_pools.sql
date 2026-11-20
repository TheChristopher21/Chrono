CREATE TABLE payroll_period_input_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    source VARCHAR(250) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    parameters LONGTEXT NOT NULL,
    PRIMARY KEY(id),
    INDEX idx_payroll_period_input_person(company_id,user_id,period_start,period_end,id),
    CONSTRAINT fk_payroll_period_input_company FOREIGN KEY(company_id) REFERENCES companies(id),
    CONSTRAINT fk_payroll_period_input_user FOREIGN KEY(user_id) REFERENCES users(id)
);
ALTER TABLE payroll_configuration_versions ADD COLUMN ktg_pool_key VARCHAR(100) NULL;
ALTER TABLE payroll_configuration_versions ADD COLUMN ktg_pool_from DATE NULL;
ALTER TABLE payroll_configuration_versions ADD COLUMN ktg_pool_through DATE NULL;
