-- Additive only: historical payslips, amounts, snapshots and time balances are untouched.
CREATE TABLE payroll_opening_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    payroll_start DATE NOT NULL,
    source VARCHAR(250) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    parameters LONGTEXT NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    discarded BIT NOT NULL DEFAULT 0,
    supersedes_opening_id BIGINT NULL,
    correction_batch VARCHAR(36) NULL,
    PRIMARY KEY (id),
    INDEX idx_payroll_opening_person_year (company_id,user_id,payroll_start,id),
    INDEX idx_payroll_opening_batch (correction_batch),
    CONSTRAINT fk_payroll_opening_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_payroll_opening_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_payroll_opening_previous FOREIGN KEY (supersedes_opening_id) REFERENCES payroll_opening_versions(id)
);
