CREATE TABLE pms_housekeeping_commands (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 property_id BIGINT NOT NULL,
 command_id VARCHAR(36) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 actor VARCHAR(120) NOT NULL,
 result_json LONGTEXT NOT NULL,
 created_at DATETIME NOT NULL,
 CONSTRAINT fk_pms_hk_command_property FOREIGN KEY (property_id) REFERENCES pms_properties(id),
 CONSTRAINT ux_pms_hk_command UNIQUE (property_id, command_id)
);
