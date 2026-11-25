-- READ ONLY. Run against the intended database using a read-only account.
-- Save results securely on the operator's machine; do not paste payroll/time data
-- into tickets. A full tested backup remains the authoritative recovery source.
-- Run only sections whose tables/columns exist. The AFTER section cannot run
-- before V57-V59. Do not execute this whole file blindly on an older schema.

-- BEFORE AND AFTER: inspect installed schema without modifying it.
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('companies', 'users', 'payslips', 'time_tracking',
      'time_tracking_entries', 'gl_accounts', 'gl_journal_entries',
      'gl_journal_entry_lines', 'fa_assets', 'schedule_rules', 'nfc_commands',
      'pms_payment_requests')
ORDER BY table_name, ordinal_position;

-- Exactly one operator-confirmed company must be returned for the live NFC reader.
-- This is inventory only, never a basis for bulk ownership backfill.
SELECT id, name FROM companies WHERE name = 'Praxis' ORDER BY id;
SELECT id, name FROM companies ORDER BY id;
SELECT id, company_id, tracking_balance_in_minutes FROM users ORDER BY id;

-- Stable existing payroll values. Do not recalculate/reapprove to test migration.
SELECT id, user_id, period_start, period_end, gross_salary, deductions, net_salary,
       allowances, bonuses, one_time_payments, tax_free_allowances,
       overtime_hours, payout_overtime, employer_contributions, payout_date,
       pay_type, currency, locked, approved, pdf_path, version
FROM payslips ORDER BY id;

SELECT id, user_id, daily_date, work_start, break_start, break_end, work_end,
       corrected, daily_note
FROM time_tracking ORDER BY id;

SELECT id, user_id, customer_id, project_id, task_id, entry_timestamp, punch_type,
       source, corrected_by_user, system_generated_note,
       correction_admin_username, correction_admin_initials, duration_minutes,
       description, approved
FROM time_tracking_entries ORDER BY id;

-- Original accounting/NFC/shift columns remain byte-for-byte/logically unchanged.
-- SELECT * captures additional pre-existing columns on this installation.
-- Before/after comparison must ignore ONLY the new ownership/default columns.
SELECT * FROM gl_accounts ORDER BY id;
SELECT * FROM gl_journal_entries ORDER BY id;
SELECT * FROM gl_journal_entry_lines ORDER BY id;
SELECT * FROM fa_assets ORDER BY id;
SELECT * FROM schedule_rules ORDER BY id;
SELECT * FROM nfc_commands ORDER BY id;

-- Existing payment provider identities must remain exactly unchanged after V58.
-- This does not request a payment, retry, refund or provider connection.
SELECT pr.id, p.company_id, r.property_id, r.id AS reservation_id, pr.folio_id,
       pr.request_key, pr.created_by, pr.kind, pr.status,
       pr.provider_session_id, pr.provider_payment_intent_id
FROM pms_payment_requests pr
JOIN pms_folios f ON f.id = pr.folio_id
JOIN pms_reservations r ON r.id = f.reservation_id
JOIN pms_properties p ON p.id = r.property_id
ORDER BY pr.id;

-- AFTER V57-V59 ONLY, before ordinary application traffic resumes.
-- Pre-existing accounting and NFC rows retain NULL ownership (no guessed tenant).
SELECT id, company_id FROM gl_accounts ORDER BY id;
SELECT id, company_id FROM gl_journal_entries ORDER BY id;
SELECT id, company_id FROM fa_assets ORDER BY id;
SELECT id, company_id, device_id, status FROM nfc_commands ORDER BY id;
SELECT id, company_id, shift_key, suppressed FROM schedule_rules ORDER BY id;
SELECT id, folio_id, request_key, client_request_key, request_source,
       automatic_reservation_id
FROM pms_payment_requests ORDER BY id;
