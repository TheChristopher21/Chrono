package com.chrono.chrono.services.payroll;

import java.math.*;

/** Dated disclosure, not a guess based on an employee's other Chrono accounts. */
public record PayrollMultipleEmployment(String method, BigDecimal ownPercent, BigDecimal totalPercent,
        BigDecimal otherPeriodicMonthlyGross, BigDecimal otherAperiodicGross, Boolean mainGermanEmployment) {
    public BigDecimal periodicRateIncome(BigDecimal own, int months) {
        return switch(method) {
            case "TOTAL_PERCENT" -> own.multiply(totalPercent).divide(ownPercent,12,RoundingMode.HALF_UP);
            case "UNKNOWN_TOTAL_PERCENT" -> own.multiply(BigDecimal.valueOf(100)).divide(ownPercent,12,RoundingMode.HALF_UP);
            case "KNOWN_OTHER_INCOME" -> own.add(otherPeriodicMonthlyGross.multiply(BigDecimal.valueOf(months)));
            default -> own;
        };
    }
    public BigDecimal additionalAperiodic() { return otherAperiodicGross==null?BigDecimal.ZERO:otherAperiodicGross; }
}
