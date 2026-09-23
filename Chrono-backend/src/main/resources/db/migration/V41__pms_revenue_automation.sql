ALTER TABLE pms_rate_overrides ADD COLUMN revenue_managed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE pms_revenue_automation (
 property_id BIGINT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 snapshots_enabled BOOLEAN NOT NULL DEFAULT TRUE, pricing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 horizon_days INT NOT NULL DEFAULT 30, min_samples INT NOT NULL DEFAULT 8, rules_json TEXT NOT NULL,
 CONSTRAINT fk_pms_revenue_automation_property FOREIGN KEY (property_id) REFERENCES pms_properties(id)
);
CREATE TABLE pms_revenue_price_runs (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, property_id BIGINT NOT NULL, business_date DATE NOT NULL, applied INT NOT NULL,
 CONSTRAINT uk_pms_price_run UNIQUE(property_id,business_date),
 CONSTRAINT fk_pms_price_run_property FOREIGN KEY (property_id) REFERENCES pms_properties(id)
);
