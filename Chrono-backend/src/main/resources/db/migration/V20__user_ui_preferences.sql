CREATE TABLE user_ui_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    company_id BIGINT NULL,
    property_id BIGINT NULL,
    tenant_key VARCHAR(80) NOT NULL,
    area VARCHAR(40) NOT NULL,
    context_key VARCHAR(80) NOT NULL,
    schema_version INT NOT NULL,
    payload TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ui_pref_user_tenant_area_context
        UNIQUE (user_id, tenant_key, area, context_key),
    CONSTRAINT fk_ui_pref_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ui_pref_company
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_ui_pref_property
        FOREIGN KEY (property_id) REFERENCES pms_properties (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_ui_pref_company_user_area
    ON user_ui_preferences (company_id, user_id, area);

CREATE INDEX idx_ui_pref_property
    ON user_ui_preferences (property_id);
