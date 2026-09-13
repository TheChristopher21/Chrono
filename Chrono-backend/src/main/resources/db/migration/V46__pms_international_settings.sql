CREATE TABLE pms_international_settings (
 property_id BIGINT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, rule_code VARCHAR(40) NOT NULL,
 jurisdiction_label VARCHAR(180) NOT NULL, source_reference VARCHAR(500) NOT NULL, required_fields_json TEXT NOT NULL,
 tax_scheme VARCHAR(10) NOT NULL DEFAULT 'VAT', zero_tax_category VARCHAR(3), zero_tax_reason VARCHAR(500),
 CONSTRAINT fk_pms_international_property FOREIGN KEY(property_id) REFERENCES pms_properties(id)
);
ALTER TABLE pms_guest_registrations ADD COLUMN required_fields_snapshot TEXT;
CREATE TABLE pms_structured_invoices (
 invoice_id BIGINT PRIMARY KEY, xml_document MEDIUMTEXT NOT NULL, content_hash VARCHAR(64) NOT NULL,
 created_at DATETIME NOT NULL, created_by VARCHAR(120) NOT NULL, standard VARCHAR(40) NOT NULL,
 CONSTRAINT fk_pms_structured_invoice FOREIGN KEY(invoice_id) REFERENCES pms_invoices(id)
);
