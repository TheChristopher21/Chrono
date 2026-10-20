-- No backfill: existing nullable values have no evidenced effective date.
create table payroll_configuration_versions (
    id bigint not null auto_increment primary key,
    company_id bigint not null,
    user_id bigint null,
    effective_from date not null,
    source varchar(250) not null,
    created_by bigint not null,
    created_at datetime(6) not null,
    uvg_bu_rate decimal(11,8) null,
    uvg_nbu_rate decimal(11,8) null,
    ktg_rate_employee decimal(11,8) null,
    ktg_rate_employer decimal(11,8) null,
    fak_rate decimal(11,8) null,
    bvg_employee decimal(12,2) null,
    bvg_employer decimal(12,2) null,
    constraint fk_payroll_config_company foreign key (company_id) references companies(id) on delete cascade,
    constraint fk_payroll_config_user foreign key (user_id) references users(id) on delete cascade
);
create index idx_payroll_config_effective on payroll_configuration_versions(company_id, user_id, effective_from, id);

