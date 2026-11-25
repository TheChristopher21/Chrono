-- Append-only rules. Existing payslip amounts are not recalculated or backfilled.
alter table payroll_configuration_versions add column fak_employee_rate decimal(11,8) null;
alter table payroll_configuration_versions add column ahv_admin_rate decimal(11,8) null;
alter table payroll_configuration_versions add column ktg_annual_salary_cap decimal(12,2) null;
create table payroll_terms_versions (
    id bigint not null auto_increment primary key,
    company_id bigint not null,
    user_id bigint not null,
    effective_from date not null,
    source varchar(250) not null,
    created_by bigint not null,
    created_at datetime(6) not null,
    parameters longtext not null,
    constraint fk_payroll_terms_company foreign key (company_id) references companies(id),
    constraint fk_payroll_terms_user foreign key (user_id) references users(id)
);
create index idx_payroll_terms_effective on payroll_terms_versions(company_id, user_id, effective_from, id);
alter table payslips add column calculation_version varchar(40) null;
alter table payslips add column calculation_snapshot longtext null;
alter table payslips add column currency varchar(3) null;
