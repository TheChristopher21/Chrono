-- Additive; no historical document, calculation or amount is rewritten.
ALTER TABLE payroll_configuration_versions ADD COLUMN uvg_pool_key VARCHAR(100) NULL;
ALTER TABLE payroll_configuration_versions ADD COLUMN uvg_pool_from DATE NULL;
ALTER TABLE payroll_configuration_versions ADD COLUMN uvg_pool_through DATE NULL;
