-- One-off repair for the legacy, UNAPPROVED draft #94, investigated 2026-10-21.
-- Requires a verified backup of the draft, components and audit before running.
-- Run with the MySQL batch client WITHOUT --force: any failed CHECK aborts the
-- connection and rolls back. Deliberately refuses a changed or already repaired
-- draft. Does not approve, send mail, change times, or post a journal entry.
-- Fractional amounts are converted to DECIMAL before rounding: MySQL's ROUND
-- on the legacy DOUBLE values does not reproduce Java's HALF_UP consistently.

CREATE TEMPORARY TABLE payslip_rounding_assertions (
    assertion_name VARCHAR(64) PRIMARY KEY,
    passed BOOLEAN NOT NULL CHECK (passed = TRUE)
) ENGINE=InnoDB;

START TRANSACTION;
SELECT id FROM users WHERE id = 62 FOR UPDATE;
SELECT id FROM payslips WHERE id = 94 FOR UPDATE;

INSERT INTO payslip_rounding_assertions
SELECT 'unchanged_open_draft', COUNT(*) = 1 FROM payslips
WHERE id = 94 AND user_id = 62 AND approved = 0 AND locked = 0 AND version = 2
  AND period_start = '2026-08-24' AND period_end = '2026-09-23'
  AND payout_date = '2026-09-25' AND pdf_path IS NULL
  AND ABS(gross_salary - 18.775) < 0.000000001
  AND ABS(deductions - 1.30) < 0.000000001
  AND ABS(net_salary - 17.475) < 0.000000001
  AND ABS(employer_contributions - 1.82) < 0.000000001
  AND payout_overtime = 1 AND ABS(overtime_hours * 60 - 17) < 0.000000001;

INSERT INTO payslip_rounding_assertions
SELECT 'unposted', COUNT(*) = 0 FROM gl_journal_entries WHERE document_reference = 'PAYSLIP-94';
INSERT INTO payslip_rounding_assertions
SELECT 'original_earnings', COUNT(*) = 2 AND SUM(
    (type = 'Base salary' AND ABS(amount - 18.066666666666666) < 0.000000001)
    OR (type = 'Overtime payout' AND ABS(amount - 0.7083333333333333) < 0.000000001)
) = 2 FROM payslip_earnings WHERE payslip_id = 94;
INSERT INTO payslip_rounding_assertions
SELECT 'original_deductions', COUNT(*) = 4 AND SUM(ROUND(CAST(amount AS DECIMAL(24,10)), 2)) = 1.30
FROM payslip_deductions WHERE payslip_id = 94;
INSERT INTO payslip_rounding_assertions
SELECT 'original_employer_contributions', COUNT(*) = 4 AND SUM(ROUND(CAST(amount AS DECIMAL(24,10)), 2)) = 1.82
FROM payslip_employer_contribs WHERE payslip_id = 94;

UPDATE payslip_earnings
SET amount = ROUND(CAST(amount AS DECIMAL(24,10)), 2)
WHERE payslip_id = 94;
INSERT INTO payslip_rounding_assertions VALUES ('two_earnings_corrected', ROW_COUNT() = 2);

UPDATE payslips
SET gross_salary = 18.78, net_salary = 17.48, version = version + 1
WHERE id = 94 AND approved = 0 AND locked = 0 AND version = 2;
INSERT INTO payslip_rounding_assertions VALUES ('one_draft_corrected', ROW_COUNT() = 1);

INSERT INTO payslip_audit (payslip_id, action, author, comment, timestamp)
VALUES (94, 'ROUNDING_CORRECTED', 'CODEX_ADMIN_REPAIR',
    'User-requested rounding repair: base 18.0666666667 -> 18.07; payout 0.7083333333 -> 0.71; gross 18.775 -> 18.78; net 17.474999999999998 -> 17.48. Deductions 1.30, employer 1.82 and 17 minutes unchanged. Backup verified; approval remains pending.',
    UTC_TIMESTAMP(6));

INSERT INTO payslip_rounding_assertions
SELECT 'balanced_and_still_open', COUNT(*) = 1 FROM payslips
WHERE id = 94 AND approved = 0 AND locked = 0 AND version = 3
  AND CAST(gross_salary AS DECIMAL(12,2)) - CAST(deductions AS DECIMAL(12,2)) = CAST(net_salary AS DECIMAL(12,2))
  AND CAST(gross_salary AS DECIMAL(12,2)) = (SELECT SUM(CAST(amount AS DECIMAL(12,2))) FROM payslip_earnings WHERE payslip_id = 94)
  AND ABS(overtime_hours * 60 - 17) < 0.000000001;
COMMIT;

SELECT id, gross_salary, deductions, net_salary, employer_contributions,
       ROUND(overtime_hours * 60) AS overtime_minutes, approved + 0 AS approved, version
FROM payslips WHERE id = 94;
