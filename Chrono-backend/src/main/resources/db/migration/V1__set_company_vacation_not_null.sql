UPDATE vacation_requests SET company_vacation = FALSE WHERE company_vacation IS NULL;
ALTER TABLE vacation_requests MODIFY company_vacation BOOLEAN NOT NULL DEFAULT FALSE;
