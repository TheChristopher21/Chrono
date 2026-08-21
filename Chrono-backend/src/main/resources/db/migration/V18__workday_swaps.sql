create table workday_swaps (
    id bigint not null auto_increment,
    user_id bigint not null,
    original_work_date date not null,
    replacement_work_date date not null,
    transferred_minutes integer not null,
    note varchar(500),
    created_by varchar(255) not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint fk_workday_swaps_user foreign key (user_id) references users (id),
    constraint uk_workday_swaps_user_original unique (user_id, original_work_date),
    constraint uk_workday_swaps_user_replacement unique (user_id, replacement_work_date)
) engine=InnoDB;

create index idx_workday_swaps_user_dates
    on workday_swaps (user_id, original_work_date, replacement_work_date);
