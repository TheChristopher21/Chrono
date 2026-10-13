alter table pms_invoice_lines add column source_item_id bigint;
alter table pms_invoice_lines add column active_source_item_id bigint;
alter table pms_invoice_lines add constraint fk_pms_invoice_line_source foreign key (source_item_id) references pms_folio_items(id);
alter table pms_invoice_lines add constraint uk_pms_invoice_line_active_source unique (active_source_item_id);
create index idx_pms_invoice_line_source on pms_invoice_lines(source_item_id);
alter table pms_invoices add column correction_mode varchar(24);

create table pms_financial_periods (
    property_id bigint not null primary key,
    business_date date not null,
    last_closed_date date,
    constraint fk_pms_financial_period_property foreign key (property_id) references pms_properties(id)
);

alter table pms_payments modify column method varchar(24) not null;

create table pms_credit_accounts (
    id bigint not null auto_increment primary key,
    property_id bigint not null,
    organization_id bigint not null,
    enabled boolean not null default false,
    credit_limit decimal(14,2) not null default 0,
    payment_terms_days integer not null default 30,
    constraint uk_pms_credit_account_property_org unique(property_id,organization_id),
    constraint fk_pms_credit_account_property foreign key(property_id) references pms_properties(id),
    constraint fk_pms_credit_account_org foreign key(organization_id) references pms_organizations(id)
);
create table pms_receivables (
    id bigint not null auto_increment primary key,
    property_id bigint not null,
    organization_id bigint not null,
    invoice_id bigint not null,
    amount decimal(14,2) not null,
    settled_amount decimal(14,2) not null default 0,
    credited_amount decimal(14,2) not null default 0,
    due_date date not null,
    created_at timestamp not null,
    created_by varchar(120) not null,
    reminder_level integer not null default 0,
    last_reminder_at timestamp,
    constraint uk_pms_receivable_invoice unique(invoice_id),
    constraint fk_pms_receivable_property foreign key(property_id) references pms_properties(id),
    constraint fk_pms_receivable_org foreign key(organization_id) references pms_organizations(id),
    constraint fk_pms_receivable_invoice foreign key(invoice_id) references pms_invoices(id)
);
create index idx_pms_receivable_property_org_due on pms_receivables(property_id,organization_id,due_date);
create table pms_receivable_settlements (
    id bigint not null auto_increment primary key,
    property_id bigint not null,
    receivable_id bigint not null,
    amount decimal(14,2) not null,
    posting_date date not null,
    request_id varchar(64) not null,
    bank_reference varchar(180) not null,
    created_at timestamp not null,
    created_by varchar(120) not null,
    constraint uk_pms_ar_settlement_request unique(property_id,request_id),
    constraint fk_pms_ar_settlement_property foreign key(property_id) references pms_properties(id),
    constraint fk_pms_ar_settlement_receivable foreign key(receivable_id) references pms_receivables(id)
);
create index idx_pms_ar_settlement_property_date on pms_receivable_settlements(property_id,posting_date);
