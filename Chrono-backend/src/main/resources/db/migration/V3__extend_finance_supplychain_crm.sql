CREATE TABLE IF NOT EXISTS gl_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    parent_id BIGINT NULL,
    active BIT NOT NULL DEFAULT 1,
    notes VARCHAR(512),
    CONSTRAINT fk_gl_account_parent FOREIGN KEY (parent_id) REFERENCES gl_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gl_journal_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entry_date DATE NOT NULL,
    description VARCHAR(512),
    source VARCHAR(128),
    document_reference VARCHAR(128),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gl_journal_entry_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    journal_entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    debit DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit DECIMAL(19,4) NOT NULL DEFAULT 0,
    memo VARCHAR(256),
    CONSTRAINT fk_jel_entry FOREIGN KEY (journal_entry_id) REFERENCES gl_journal_entries(id),
    CONSTRAINT fk_jel_account FOREIGN KEY (account_id) REFERENCES gl_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ar_customer_invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    invoice_number VARCHAR(255) NOT NULL UNIQUE,
    invoice_date DATE,
    due_date DATE,
    amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    currency VARCHAR(3),
    status VARCHAR(32) NOT NULL,
    project_id BIGINT,
    journal_entry_id BIGINT,
    notes VARCHAR(512),
    CONSTRAINT fk_ar_journal FOREIGN KEY (journal_entry_id) REFERENCES gl_journal_entries(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ap_vendor_invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_name VARCHAR(255) NOT NULL,
    invoice_number VARCHAR(255) NOT NULL UNIQUE,
    invoice_date DATE,
    due_date DATE,
    amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    currency VARCHAR(3),
    status VARCHAR(32) NOT NULL,
    journal_entry_id BIGINT,
    notes VARCHAR(512),
    CONSTRAINT fk_ap_journal FOREIGN KEY (journal_entry_id) REFERENCES gl_journal_entries(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fa_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_name VARCHAR(255) NOT NULL,
    acquisition_date DATE NOT NULL,
    acquisition_cost DECIMAL(19,4) NOT NULL,
    useful_life_months INT NOT NULL,
    residual_value DECIMAL(19,4) DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    accumulated_depreciation DECIMAL(19,4) DEFAULT 0,
    last_depreciation_date DATE,
    journal_entry_id BIGINT,
    CONSTRAINT fk_asset_journal FOREIGN KEY (journal_entry_id) REFERENCES gl_journal_entries(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inv_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    unit_of_measure VARCHAR(32),
    unit_cost DECIMAL(19,4) DEFAULT 0,
    unit_price DECIMAL(19,4) DEFAULT 0,
    active BIT NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inv_warehouses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(512)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inv_stock_levels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity DECIMAL(19,4) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_product_warehouse (product_id, warehouse_id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES inv_products(id),
    CONSTRAINT fk_stock_warehouse FOREIGN KEY (warehouse_id) REFERENCES inv_warehouses(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS inv_stock_movements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    quantity_change DECIMAL(19,4) NOT NULL,
    movement_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference VARCHAR(128),
    notes VARCHAR(512),
    CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES inv_products(id),
    CONSTRAINT fk_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES inv_warehouses(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS scm_purchase_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(255) NOT NULL UNIQUE,
    vendor_name VARCHAR(255) NOT NULL,
    order_date DATE,
    expected_date DATE,
    status VARCHAR(32) NOT NULL,
    total_amount DECIMAL(19,4) DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS scm_purchase_order_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    unit_cost DECIMAL(19,4) NOT NULL,
    CONSTRAINT fk_pol_order FOREIGN KEY (purchase_order_id) REFERENCES scm_purchase_orders(id),
    CONSTRAINT fk_pol_product FOREIGN KEY (product_id) REFERENCES inv_products(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS scm_sales_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(255) NOT NULL UNIQUE,
    customer_name VARCHAR(255) NOT NULL,
    order_date DATE,
    due_date DATE,
    status VARCHAR(32) NOT NULL,
    total_amount DECIMAL(19,4) DEFAULT 0,
    project_id BIGINT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS scm_sales_order_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sales_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    CONSTRAINT fk_sol_order FOREIGN KEY (sales_order_id) REFERENCES scm_sales_orders(id),
    CONSTRAINT fk_sol_product FOREIGN KEY (product_id) REFERENCES inv_products(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS scm_production_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(255) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_date DATE,
    completion_date DATE,
    CONSTRAINT fk_production_product FOREIGN KEY (product_id) REFERENCES inv_products(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS scm_service_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    description VARCHAR(2048),
    status VARCHAR(32) NOT NULL,
    opened_date DATE,
    closed_date DATE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS crm_customer_addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    street VARCHAR(255),
    postal_code VARCHAR(64),
    city VARCHAR(255),
    country VARCHAR(255),
    CONSTRAINT fk_crm_address_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS crm_customer_contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(64),
    role_title VARCHAR(255),
    CONSTRAINT fk_crm_contact_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS crm_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT,
    contact_id BIGINT,
    type VARCHAR(32) NOT NULL,
    notes VARCHAR(2048),
    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner VARCHAR(255),
    CONSTRAINT fk_crm_activity_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_crm_activity_contact FOREIGN KEY (contact_id) REFERENCES crm_customer_contacts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS crm_leads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    company_name VARCHAR(255),
    contact_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_date DATE,
    source VARCHAR(255),
    CONSTRAINT fk_crm_lead_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS crm_opportunities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    customer_id BIGINT,
    title VARCHAR(255),
    opportunity_value DECIMAL(19,4) DEFAULT 0,
    expected_close_date DATE,
    stage VARCHAR(32) NOT NULL,
    probability DOUBLE,
    CONSTRAINT fk_crm_opportunity_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_crm_opportunity_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
 ) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS crm_campaigns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(255),
    start_date DATE,
    end_date DATE,
    status VARCHAR(32) NOT NULL,
    channel VARCHAR(255),
    budget INT,
    CONSTRAINT fk_crm_campaign_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS crm_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT,
    file_name VARCHAR(255),
    url VARCHAR(512),
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(255),
    CONSTRAINT fk_crm_document_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bank_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    iban VARCHAR(255) NOT NULL,
    bic VARCHAR(64),
    clearing_number VARCHAR(64),
    CONSTRAINT fk_bank_account_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bank_payment_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    bank_account_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at DATETIME,
    transmitted_at DATETIME,
    approval_by VARCHAR(255),
    transmission_reference VARCHAR(255),
    CONSTRAINT fk_batch_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_batch_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bank_payment_instructions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    creditor_name VARCHAR(255),
    creditor_iban VARCHAR(255),
    creditor_bic VARCHAR(64),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3),
    reference VARCHAR(255),
    vendor_invoice_id BIGINT,
    customer_invoice_id BIGINT,
    CONSTRAINT fk_instruction_batch FOREIGN KEY (batch_id) REFERENCES bank_payment_batches(id),
    CONSTRAINT fk_instruction_vendor FOREIGN KEY (vendor_invoice_id) REFERENCES ap_vendor_invoices(id),
    CONSTRAINT fk_instruction_customer FOREIGN KEY (customer_invoice_id) REFERENCES ar_customer_invoices(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS digital_signature_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_type VARCHAR(255),
    document_path VARCHAR(512),
    signer_email VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    provider_reference VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS secure_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    recipient VARCHAR(255),
    subject VARCHAR(255),
    body VARCHAR(2048),
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered BIT NOT NULL DEFAULT 0,
    transport VARCHAR(255),
    CONSTRAINT fk_secure_message_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB;
