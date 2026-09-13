CREATE TABLE pms_rate_inheritance (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, source_rate_id BIGINT NOT NULL, target_rate_id BIGINT NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE, frozen BOOLEAN NOT NULL DEFAULT FALSE, inherit_policy BOOLEAN NOT NULL DEFAULT FALSE,
 manual_fx_rate DECIMAL(19,8) NOT NULL, fx_date DATE NOT NULL, fx_valid_until DATE NOT NULL, fx_reference VARCHAR(240) NOT NULL,
 adjustment_percent DECIMAL(9,4) NOT NULL, min_price DECIMAL(19,4) NOT NULL, max_price DECIMAL(19,4) NOT NULL,
 last_source_hash VARCHAR(64), last_applied_at DATETIME, last_result VARCHAR(1000),
 CONSTRAINT uk_pms_rate_inheritance_target UNIQUE(target_rate_id),
 CONSTRAINT fk_pms_rate_inheritance_source FOREIGN KEY(source_rate_id) REFERENCES pms_rate_plans(id),
 CONSTRAINT fk_pms_rate_inheritance_target FOREIGN KEY(target_rate_id) REFERENCES pms_rate_plans(id)
);
