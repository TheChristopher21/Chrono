alter table pms_guests add column reference_code varchar(20);
alter table pms_guests add column address_line_1 varchar(180);
alter table pms_guests add column postal_code varchar(20);
alter table pms_guests add column city varchar(120);
alter table pms_guests add column country_code varchar(2);
alter table pms_guests add column vehicle_plate varchar(40);
alter table pms_guests add column room_preferences varchar(1000);
alter table pms_guests add column organization_id bigint;
alter table pms_guests add column merged_into_id bigint;
alter table pms_guests add column active bit not null default 1;

update pms_guests
set reference_code = concat('GK', lpad(id, 6, '0'))
where reference_code is null;

create unique index uk_pms_guest_company_reference on pms_guests (company_id, reference_code);
create index idx_pms_guest_organization on pms_guests (organization_id);
create index idx_pms_guest_merged_into on pms_guests (merged_into_id);
alter table pms_guests add constraint fk_pms_guest_organization
    foreign key (organization_id) references pms_organizations (id);
alter table pms_guests add constraint fk_pms_guest_merged_into
    foreign key (merged_into_id) references pms_guests (id);

alter table pms_organizations add column reference_code varchar(20);
alter table pms_organizations add column master_record bit not null default 0;
alter table pms_organizations add column parent_organization_id bigint;
alter table pms_organizations add column merged_into_id bigint;

update pms_organizations
set reference_code = concat('FK', lpad(id, 6, '0'))
where reference_code is null;

create unique index uk_pms_organization_company_reference
    on pms_organizations (company_id, reference_code);
create index idx_pms_organization_parent on pms_organizations (parent_organization_id);
create index idx_pms_organization_merged_into on pms_organizations (merged_into_id);
alter table pms_organizations add constraint fk_pms_organization_parent
    foreign key (parent_organization_id) references pms_organizations (id);
alter table pms_organizations add constraint fk_pms_organization_merged_into
    foreign key (merged_into_id) references pms_organizations (id);

alter table pms_reservations add column child_ages varchar(100);
alter table pms_reservations add column guest_preference_snapshot varchar(1000);

alter table pms_rooms add column features varchar(1000);
