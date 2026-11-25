-- Append-only correction documents. Existing amounts, approvals and snapshots remain untouched.
ALTER TABLE payslips ADD COLUMN correction_of_id BIGINT NULL;
ALTER TABLE payslips ADD COLUMN supersedes_correction_id BIGINT NULL;
ALTER TABLE payslips ADD COLUMN correction_batch VARCHAR(36) NULL;
ALTER TABLE payslips ADD CONSTRAINT fk_payslip_correction_original FOREIGN KEY (correction_of_id) REFERENCES payslips(id);
ALTER TABLE payslips ADD CONSTRAINT fk_payslip_correction_previous FOREIGN KEY (supersedes_correction_id) REFERENCES payslips(id);
CREATE INDEX idx_payslip_correction_batch ON payslips(correction_batch);
CREATE INDEX idx_payslip_correction_original ON payslips(correction_of_id);
