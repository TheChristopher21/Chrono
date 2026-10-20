CREATE TABLE pms_property_grants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    permissions TEXT NOT NULL,
    CONSTRAINT uk_pms_property_grant UNIQUE (user_id, property_id),
    CONSTRAINT fk_pms_grant_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_pms_grant_property FOREIGN KEY (property_id) REFERENCES pms_properties(id) ON DELETE CASCADE
);
