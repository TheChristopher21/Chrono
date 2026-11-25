CREATE TABLE pms_revenue_snapshots (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, property_id BIGINT NOT NULL, as_of_date DATE NOT NULL,
 to_exclusive DATE NOT NULL, currency_code VARCHAR(3) NOT NULL, captured_at DATETIME NOT NULL, captured_by VARCHAR(120) NOT NULL,
 CONSTRAINT uk_pms_revenue_snapshot_day UNIQUE(property_id,as_of_date),
 CONSTRAINT fk_pms_revenue_snapshot_property FOREIGN KEY(property_id) REFERENCES pms_properties(id)
);
CREATE TABLE pms_revenue_snapshot_days (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, snapshot_id BIGINT NOT NULL, stay_date DATE NOT NULL,
 room_nights BIGINT NOT NULL, net_room_revenue DECIMAL(19,4), unknown_revenue_room_nights BIGINT NOT NULL,
 CONSTRAINT uk_pms_revenue_snapshot_stay_day UNIQUE(snapshot_id,stay_date),
 CONSTRAINT fk_pms_revenue_snapshot_day FOREIGN KEY(snapshot_id) REFERENCES pms_revenue_snapshots(id)
);
CREATE TABLE pms_revenue_budgets (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, property_id BIGINT NOT NULL,
 month_start DATE NOT NULL, currency_code VARCHAR(3) NOT NULL, room_nights BIGINT NOT NULL, net_room_revenue DECIMAL(19,4) NOT NULL,
 updated_at DATETIME NOT NULL, updated_by VARCHAR(120) NOT NULL,
 CONSTRAINT uk_pms_revenue_budget_month UNIQUE(property_id,month_start),
 CONSTRAINT fk_pms_revenue_budget_property FOREIGN KEY(property_id) REFERENCES pms_properties(id)
);
