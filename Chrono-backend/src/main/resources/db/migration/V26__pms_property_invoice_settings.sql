ALTER TABLE pms_properties ADD COLUMN tax_number VARCHAR(80) NULL;
ALTER TABLE pms_properties ADD COLUMN tax_registration_label VARCHAR(40) NOT NULL DEFAULT 'VAT / Tax ID';
ALTER TABLE pms_properties ADD COLUMN registration_number VARCHAR(100) NULL;
ALTER TABLE pms_properties ADD COLUMN address_line_2 VARCHAR(180) NULL;
ALTER TABLE pms_properties ADD COLUMN region VARCHAR(100) NULL;
ALTER TABLE pms_properties ADD COLUMN invoice_footer VARCHAR(2000) NULL;
ALTER TABLE pms_properties ADD COLUMN invoice_prefix VARCHAR(24) NOT NULL DEFAULT 'INV';
ALTER TABLE pms_properties ADD COLUMN invoice_due_days INT NOT NULL DEFAULT 14;
