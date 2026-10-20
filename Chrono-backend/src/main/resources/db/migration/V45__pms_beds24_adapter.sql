CREATE TABLE pms_beds24_settings (
 property_id BIGINT NOT NULL PRIMARY KEY,
 external_property_id BIGINT NULL,
 secret_reference VARCHAR(180) NULL,
 mappings_json TEXT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 verified_at DATETIME NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_beds24_settings_property FOREIGN KEY(property_id) REFERENCES pms_properties(id)
);
CREATE TABLE pms_beds24_publications (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 property_id BIGINT NOT NULL,
 request_key VARCHAR(80) NOT NULL,
 external_property_id BIGINT NOT NULL,
 secret_reference VARCHAR(180) NOT NULL,
 currency_code VARCHAR(3) NOT NULL,
 payload LONGTEXT NOT NULL,
 from_date DATE NOT NULL,
 to_date DATE NOT NULL,
 status VARCHAR(24) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME NOT NULL,
 created_at DATETIME NOT NULL,
 completed_at DATETIME NULL,
 last_error VARCHAR(500) NULL,
 CONSTRAINT uk_beds24_publication_request UNIQUE(property_id,request_key),
 CONSTRAINT fk_beds24_publication_property FOREIGN KEY(property_id) REFERENCES pms_properties(id)
);
CREATE INDEX idx_beds24_publication_due ON pms_beds24_publications(status,next_attempt_at);
