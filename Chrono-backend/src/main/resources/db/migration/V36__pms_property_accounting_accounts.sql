create table pms_accounting_settings (
    property_id bigint not null primary key,
    updated_at datetime(6) not null,
    updated_by varchar(120) not null,
    constraint fk_pms_accounting_property foreign key(property_id) references pms_properties(id)
);
create table pms_accounting_accounts (
    property_id bigint not null,
    account_key varchar(40) not null,
    account_code varchar(32) not null,
    primary key(property_id,account_key),
    constraint fk_pms_account_mapping_settings foreign key(property_id) references pms_accounting_settings(property_id)
);
