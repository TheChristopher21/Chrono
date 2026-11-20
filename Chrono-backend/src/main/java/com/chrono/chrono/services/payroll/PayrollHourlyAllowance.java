package com.chrono.chrono.services.payroll;

import java.math.BigDecimal;

/** Explicit contractual allowance basis. A holiday rate is never inferred from a canton. */
public record PayrollHourlyAllowance(String vacationMode, BigDecimal vacationRate, BigDecimal holidayRate,
        String allowanceBase, Boolean cashVacationPermitted, Boolean allowancesInThirteenth,
        Boolean paidAbsencesInThirteenth) {}
