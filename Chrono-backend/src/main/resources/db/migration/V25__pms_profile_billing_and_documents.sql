alter table pms_guests add column private_email varchar(190);
alter table pms_guests add column business_email varchar(190);
alter table pms_guests add column additional_emails text;
alter table pms_guests add column dietary_notes varchar(1000);
alter table pms_guests add column vat_number varchar(80);
alter table pms_guests add column organization_contact_id varchar(36);
alter table pms_guests add column billing_override bit not null default 0;
alter table pms_guests add column billing_profile text;

alter table pms_organizations add column private_email varchar(190);
alter table pms_organizations add column business_email varchar(190);
alter table pms_organizations add column additional_emails text;
alter table pms_organizations add column contacts mediumtext;
alter table pms_organizations add column billing_profile text;

alter table pms_invoices add column recipient_snapshot text;
alter table pms_invoices add column supplier_snapshot text;
alter table pms_invoices add column supplier_tax_label varchar(40);
alter table pms_invoices add column supplier_registration_number varchar(100);
alter table pms_invoices add column service_from date;
alter table pms_invoices add column service_to date;
alter table pms_invoice_lines add column vat_rate decimal(7,4);
alter table pms_invoice_lines add column service_date date;

create table pms_profile_documents (
    id bigint not null auto_increment,
    company_id bigint not null,
    organization_id bigint not null,
    rate_plan_id bigint,
    file_name varchar(180) not null,
    content_type varchar(80) not null,
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    uploaded_by varchar(120) not null,
    uploaded_at datetime(6) not null,
    content longblob not null,
    primary key (id),
    constraint fk_pms_document_company foreign key (company_id) references companies(id),
    constraint fk_pms_document_organization foreign key (organization_id) references pms_organizations(id),
    constraint fk_pms_document_rate foreign key (rate_plan_id) references pms_rate_plans(id),
    index idx_pms_document_tenant_organization (company_id, organization_id)
);
