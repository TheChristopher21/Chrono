ALTER TABLE pms_profile_documents ADD COLUMN storage_key VARCHAR(100) NULL;
ALTER TABLE pms_profile_documents ADD COLUMN version_group VARCHAR(36) NULL;
ALTER TABLE pms_profile_documents ADD COLUMN document_version INT NOT NULL DEFAULT 1;
ALTER TABLE pms_profile_documents ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE pms_profile_documents SET version_group = CAST(id AS CHAR);
CREATE UNIQUE INDEX ux_pms_document_version ON pms_profile_documents(company_id, version_group, document_version);
CREATE INDEX idx_pms_document_listing ON pms_profile_documents(organization_id, archived, uploaded_at);
