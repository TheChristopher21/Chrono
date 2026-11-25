-- Run only against the intended Chrono database. No names, addresses or bank data.
-- This reconciles stored arithmetic; it does not certify tax/insurance deductions.
SET SESSION TRANSACTION READ ONLY;
START TRANSACTION WITH CONSISTENT SNAPSHOT;

SELECT COUNT(*) AS payslips, SUM(approved) AS approved,
       SUM(net_salary < 0) AS negative_net,
       SUM(ABS(ROUND(gross_salary,2)-ROUND(deductions,2)-ROUND(net_salary,2)) > 0.001) AS net_mismatches
FROM payslips;

SELECT p.id AS payslip_id, p.user_id, p.period_start, p.period_end,
       p.approved+0 AS approved, p.net_salary < 0 AS negative_net,
       ABS(ROUND(p.gross_salary,2)-COALESCE(e.total,0)) > 0.001 AS earnings_mismatch,
       ABS(ROUND(p.deductions,2)-COALESCE(d.total,0)) > 0.001 AS deductions_mismatch,
       ABS(ROUND(p.employer_contributions,2)-COALESCE(c.total,0)) > 0.001 AS employer_mismatch
FROM payslips p
LEFT JOIN (SELECT payslip_id,SUM(ROUND(amount,2)) total FROM payslip_earnings GROUP BY payslip_id) e ON e.payslip_id=p.id
LEFT JOIN (SELECT payslip_id,SUM(ROUND(amount,2)) total FROM payslip_deductions GROUP BY payslip_id) d ON d.payslip_id=p.id
LEFT JOIN (SELECT payslip_id,SUM(ROUND(amount,2)) total FROM payslip_employer_contribs GROUP BY payslip_id) c ON c.payslip_id=p.id
WHERE p.net_salary < 0 OR ABS(ROUND(p.gross_salary,2)-COALESCE(e.total,0)) > 0.001
   OR ABS(ROUND(p.deductions,2)-COALESCE(d.total,0)) > 0.001
   OR ABS(ROUND(p.employer_contributions,2)-COALESCE(c.total,0)) > 0.001;

SELECT a.id AS first_payslip, b.id AS second_payslip, a.user_id,
       a.period_start AS first_start, a.period_end AS first_end,
       b.period_start AS second_start, b.period_end AS second_end
FROM payslips a JOIN payslips b ON a.user_id=b.user_id AND a.id<b.id
AND a.period_start<=b.period_end AND b.period_start<=a.period_end
WHERE a.approved=1 AND b.approved=1;

SELECT user_id, entry_timestamp, punch_type, COUNT(*) AS duplicates
FROM time_tracking_entries GROUP BY user_id,entry_timestamp,punch_type HAVING COUNT(*)>1;

SELECT u.company_id,u.country,COUNT(*) AS users,
       SUM(u.birth_date IS NULL) AS missing_birth_date,
       SUM(u.is_hourly=1 AND (u.hourly_rate IS NULL OR u.hourly_rate<=0)) AS missing_hourly_rate,
       SUM(COALESCE(u.is_hourly,0)=0 AND (u.monthly_salary IS NULL OR u.monthly_salary<=0)) AS missing_salary
FROM users u WHERE u.demo=0 AND u.deleted=0 GROUP BY u.company_id,u.country;
COMMIT;
