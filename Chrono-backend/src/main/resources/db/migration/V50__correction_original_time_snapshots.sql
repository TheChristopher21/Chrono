alter table correction_requests add column original_timestamp datetime(6) null;
alter table correction_requests add column original_punch_type enum ('ENDE','START') null;
alter table correction_requests add column original_entry_id bigint null;
alter table correction_requests add column original_day_captured bit not null default 0;

-- Preserve linked historical values before removing the live dependency.
update correction_requests cr
set original_timestamp = (select t.entry_timestamp from time_tracking_entries t where t.id = cr.target_entry_id),
    original_punch_type = (select t.punch_type from time_tracking_entries t where t.id = cr.target_entry_id),
    original_entry_id = target_entry_id
where target_entry_id is not null;

update correction_requests set target_entry_id = null where original_timestamp is not null;

create table correction_original_entries (
    correction_request_id bigint not null,
    entry_id bigint null,
    entry_timestamp datetime(6) not null,
    punch_type enum ('ENDE','START') not null,
    constraint fk_correction_original_request foreign key (correction_request_id)
        references correction_requests (id) on delete cascade
);
