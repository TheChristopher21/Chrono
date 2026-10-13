alter table time_tracking_entries
    add column correction_admin_username varchar(255) null;

alter table time_tracking_entries
    add column correction_admin_initials varchar(10) null;

alter table correction_requests
    add column processed_by_admin_username varchar(255) null;

alter table correction_requests
    add column processed_by_admin_initials varchar(10) null;
