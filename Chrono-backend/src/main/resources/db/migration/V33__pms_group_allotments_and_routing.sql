alter table pms_group_bookings add column routed_types varchar(120);
alter table pms_folios add column group_booking_id bigint;
alter table pms_folios add column group_master boolean not null default false;
alter table pms_folios add constraint fk_pms_folio_group foreign key (group_booking_id) references pms_group_bookings(id);
-- Ordinary folios leave group_booking_id null, so each group can have only one master.
create unique index uk_pms_folio_group_master on pms_folios(group_booking_id);
alter table pms_folio_items add column source_reservation_id bigint;
update pms_folio_items set source_reservation_id=(select f.reservation_id from pms_folios f where f.id=pms_folio_items.folio_id);
alter table pms_folio_items add constraint fk_pms_item_source_reservation foreign key(source_reservation_id) references pms_reservations(id);
create index idx_pms_item_source_reservation on pms_folio_items(source_reservation_id,rate_generated,service_date);
create table pms_group_allotments (
    id bigint not null auto_increment primary key,
    group_booking_id bigint not null,
    room_type_id bigint not null,
    start_date date not null,
    end_date date not null,
    quantity int not null,
    release_date date not null,
    released boolean not null default false,
    constraint fk_pms_allotment_group foreign key(group_booking_id) references pms_group_bookings(id),
    constraint fk_pms_allotment_roomtype foreign key(room_type_id) references pms_room_types(id)
);
create index idx_pms_allotment_group_dates on pms_group_allotments(group_booking_id,start_date,end_date);
