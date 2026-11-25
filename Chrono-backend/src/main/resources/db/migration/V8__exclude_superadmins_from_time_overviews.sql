-- Superadmin accounts are platform control accounts, not operational employees.
-- Keep them for login/company management, but never include them in time dashboards.

UPDATE users
SET include_in_time_tracking = FALSE
WHERE id IN (
    SELECT ur.user_id
    FROM user_roles ur
    INNER JOIN roles r ON r.id = ur.role_id
    WHERE r.role_name = 'ROLE_SUPERADMIN'
);
