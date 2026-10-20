alter table pms_resource_bookings add column occupied_from datetime(6);
alter table pms_resource_bookings add column occupied_until datetime(6);
update pms_resource_bookings set occupied_from=start_at,occupied_until=end_at;
alter table pms_resource_bookings modify column occupied_from datetime(6) not null;
alter table pms_resource_bookings modify column occupied_until datetime(6) not null;
create index idx_pms_resource_occupied on pms_resource_bookings(resource_id,occupied_from,occupied_until);
create table pms_event_orders (
    id bigint not null auto_increment primary key,
    resource_booking_id bigint not null unique,
    setup_minutes int not null default 0,
    teardown_minutes int not null default 0,
    agenda varchar(8000),
    setup_instructions varchar(4000),
    catering_notes varchar(4000),
    posted_folio_id bigint,
    posted_at datetime(6),
    posted_by varchar(120),
    constraint fk_pms_event_booking foreign key(resource_booking_id) references pms_resource_bookings(id),
    constraint fk_pms_event_folio foreign key(posted_folio_id) references pms_folios(id)
);
create table pms_event_order_lines (
    id bigint not null auto_increment primary key,
    event_order_id bigint not null,
    description varchar(240) not null,
    type varchar(24) not null,
    quantity decimal(10,2) not null,
    net_unit_price decimal(19,4) not null,
    tax_rate decimal(7,4) not null,
    net_amount decimal(19,4) not null,
    tax_amount decimal(19,4) not null,
    gross_amount decimal(19,4) not null,
    folio_item_id bigint unique,
    constraint fk_pms_event_line_order foreign key(event_order_id) references pms_event_orders(id),
    constraint fk_pms_event_line_folioitem foreign key(folio_item_id) references pms_folio_items(id)
);
create index idx_pms_event_order_line on pms_event_order_lines(event_order_id);
