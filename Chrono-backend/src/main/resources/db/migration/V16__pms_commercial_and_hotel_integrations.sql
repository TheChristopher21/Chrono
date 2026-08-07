create table pms_booking_engine_settings (
    id bigint not null auto_increment primary key,
    property_id bigint not null,
    public_slug varchar(120) not null,
    enabled boolean not null default false,
    require_guarantee boolean not null default false,
    terms_url varchar(500),
    privacy_url varchar(500),
    confirmation_message varchar(1000),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_pms_booking_engine_property unique (property_id),
    constraint uk_pms_booking_engine_slug unique (public_slug),
    constraint fk_pms_booking_engine_property foreign key (property_id) references pms_properties(id)
);

create table pms_tourism_tax_rules (
    id bigint not null auto_increment primary key,
    property_id bigint not null,
    enabled boolean not null default false,
    name varchar(120) not null,
    adult_rate decimal(12,2) not null,
    child_rate decimal(12,2) not null,
    child_free_under integer not null,
    maximum_nights integer,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_pms_tourism_tax_property unique (property_id),
    constraint fk_pms_tourism_tax_property foreign key (property_id) references pms_properties(id)
);

create table pms_tourism_tax_postings (
    id bigint not null auto_increment primary key,
    property_id bigint not null,
    reservation_id bigint not null,
    folio_id bigint not null,
    folio_item_id bigint not null,
    amount decimal(12,2) not null,
    nights integer not null,
    posted_at timestamp not null,
    posted_by varchar(120) not null,
    constraint uk_pms_tourism_tax_reservation unique (reservation_id),
    constraint fk_pms_tourism_tax_posting_property foreign key (property_id) references pms_properties(id),
    constraint fk_pms_tourism_tax_posting_reservation foreign key (reservation_id) references pms_reservations(id),
    constraint fk_pms_tourism_tax_posting_folio foreign key (folio_id) references pms_folios(id),
    constraint fk_pms_tourism_tax_posting_item foreign key (folio_item_id) references pms_folio_items(id)
);

create table pms_pos_tickets (
    id bigint not null auto_increment primary key,
    property_id bigint not null,
    folio_id bigint,
    ticket_number varchar(50) not null,
    outlet_code varchar(32) not null,
    table_reference varchar(60),
    service_date date not null,
    status varchar(20) not null,
    payment_method varchar(24),
    currency_code varchar(3) not null,
    net_amount decimal(12,2) not null,
    tax_amount decimal(12,2) not null,
    gross_amount decimal(12,2) not null,
    created_by varchar(120) not null,
    created_at timestamp not null,
    settled_at timestamp,
    constraint uk_pms_pos_ticket_number unique (property_id, ticket_number),
    constraint fk_pms_pos_ticket_property foreign key (property_id) references pms_properties(id),
    constraint fk_pms_pos_ticket_folio foreign key (folio_id) references pms_folios(id)
);

create table pms_pos_ticket_lines (
    id bigint not null auto_increment primary key,
    ticket_id bigint not null,
    description varchar(240) not null,
    quantity decimal(10,2) not null,
    unit_price decimal(12,2) not null,
    tax_rate decimal(5,2) not null,
    net_amount decimal(12,2) not null,
    tax_amount decimal(12,2) not null,
    gross_amount decimal(12,2) not null,
    constraint fk_pms_pos_line_ticket foreign key (ticket_id) references pms_pos_tickets(id)
);

create table pms_access_credentials (
    id bigint not null auto_increment primary key,
    property_id bigint not null,
    reservation_id bigint not null,
    room_id bigint not null,
    provider_code varchar(50) not null,
    external_reference varchar(160) not null,
    status varchar(20) not null,
    valid_from timestamp not null,
    valid_until timestamp not null,
    issued_by varchar(120) not null,
    issued_at timestamp not null,
    revoked_by varchar(120),
    revoked_at timestamp,
    constraint uk_pms_access_provider_ref unique (property_id, provider_code, external_reference),
    constraint fk_pms_access_property foreign key (property_id) references pms_properties(id),
    constraint fk_pms_access_reservation foreign key (reservation_id) references pms_reservations(id),
    constraint fk_pms_access_room foreign key (room_id) references pms_rooms(id)
);

create table pms_migration_batches (
    id bigint not null auto_increment primary key,
    property_id bigint not null,
    idempotency_key varchar(120) not null,
    source_system varchar(100) not null,
    status varchar(20) not null,
    imported_guests integer not null,
    imported_reservations integer not null,
    imported_payments integer not null,
    total_opening_balance decimal(14,2) not null,
    reconciliation_message varchar(1000),
    created_by varchar(120) not null,
    created_at timestamp not null,
    completed_at timestamp,
    constraint uk_pms_migration_batch_key unique (property_id, idempotency_key),
    constraint fk_pms_migration_property foreign key (property_id) references pms_properties(id)
);

create index idx_pms_pos_property_created on pms_pos_tickets(property_id, created_at);
create index idx_pms_access_property_status on pms_access_credentials(property_id, status);
create index idx_pms_migration_property_created on pms_migration_batches(property_id, created_at);
