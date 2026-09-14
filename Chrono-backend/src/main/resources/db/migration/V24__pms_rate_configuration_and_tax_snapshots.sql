ALTER TABLE pms_rate_plans ADD COLUMN vat_rate NUMERIC(7,4);
ALTER TABLE pms_rate_plans ADD COLUMN tax_included BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE pms_rate_plans ADD COLUMN breakfast_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE pms_rate_plans ADD COLUMN breakfast_vat_rate NUMERIC(7,4);
ALTER TABLE pms_rate_plans ADD COLUMN valid_from DATE;
ALTER TABLE pms_rate_plans ADD COLUMN valid_to DATE;
ALTER TABLE pms_rate_plans ADD COLUMN booking_from DATE;
ALTER TABLE pms_rate_plans ADD COLUMN booking_to DATE;
ALTER TABLE pms_rate_plans ADD COLUMN max_stay INTEGER;
ALTER TABLE pms_rate_plans ADD COLUMN min_advance_days INTEGER;
ALTER TABLE pms_rate_plans ADD COLUMN max_advance_days INTEGER;
ALTER TABLE pms_rate_plans ADD COLUMN included_adults INTEGER NOT NULL DEFAULT 1;
ALTER TABLE pms_rate_plans ADD COLUMN extra_adult_rate NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE pms_rate_plans ADD COLUMN child_rate NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE pms_rate_plans ADD COLUMN cancellation_deadline_hours INTEGER;
ALTER TABLE pms_rate_plans ADD COLUMN cancellation_fee_percent NUMERIC(7,4);
ALTER TABLE pms_rate_plans ADD COLUMN deposit_percent NUMERIC(7,4);
ALTER TABLE pms_rate_plans ADD COLUMN payment_due_days INTEGER;
ALTER TABLE pms_rate_plans ADD COLUMN cancellation_policy VARCHAR(2000);
ALTER TABLE pms_rate_plans ADD COLUMN payment_policy VARCHAR(2000);
ALTER TABLE pms_rate_plans ADD COLUMN notes VARCHAR(4000);
ALTER TABLE pms_rate_plans ADD COLUMN organization_id BIGINT REFERENCES pms_organizations(id);
CREATE INDEX idx_pms_rate_plan_organization ON pms_rate_plans(organization_id);

ALTER TABLE pms_folio_items ADD COLUMN tax_rate NUMERIC(7,4);
ALTER TABLE pms_folio_items ADD COLUMN tax_included BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE pms_folio_items ADD COLUMN rate_generated BOOLEAN NOT NULL DEFAULT FALSE;
-- Existing accommodation lines were generated from the rate, but have no reliable historical tax rate.
UPDATE pms_folio_items SET rate_generated = TRUE WHERE type = 'ROOM';
