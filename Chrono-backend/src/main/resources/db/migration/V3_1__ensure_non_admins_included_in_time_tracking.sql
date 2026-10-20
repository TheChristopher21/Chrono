-- Ensure that regular (non-admin) users are always visible in weekly time tracking views.
-- Some existing installations accidentally persisted `include_in_time_tracking = FALSE`
-- for regular employees after introducing the opt-out toggle for administrators.
-- Since the frontend hides the toggle for ROLE_USER accounts, these users could not
-- be re-enabled manually and disappeared from the admin dashboard overview.
--
-- This migration forces the flag back to TRUE for every user that does not hold an
-- administrative role while keeping the explicit opt-out for admins intact.

UPDATE users
SET include_in_time_tracking = TRUE
WHERE include_in_time_tracking = FALSE
  AND id NOT IN (
    SELECT ur.user_id
    FROM user_roles ur
    INNER JOIN roles r ON r.id = ur.role_id
    WHERE r.role_name IN ('ROLE_ADMIN', 'ROLE_SUPERADMIN')
);
