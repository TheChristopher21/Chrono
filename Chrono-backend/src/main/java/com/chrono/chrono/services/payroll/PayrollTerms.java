package com.chrono.chrono.services.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Explicit, dated inputs. Null is unknown, never an implied zero or exemption. */
public record PayrollTerms(String country, String payType, BigDecimal amount, BigDecimal weeklyHours,
        BigDecimal overtimeHourlyRate, BigDecimal overtimePremiumRate, Integer weeklyLimit,
        String taxMode, String canton, String tariffCode, Boolean monthlySingleEmployment,
        LocalDate birthDate, LocalDate entryDate,
        Integer taxClass, BigDecimal childAllowance, BigDecimal churchRate, BigDecimal healthAdditionalRate,
        Boolean parent, Integer childrenUnder25, Boolean saxony, BigDecimal taxAllowance,
        BigDecimal taxAddition, BigDecimal taxFactor, BigDecimal u1Rate, BigDecimal u2Rate,
        BigDecimal insolvencyRate, PayrollContract contract) {
    public PayrollTerms(String country, String payType, BigDecimal amount, BigDecimal weeklyHours,
            BigDecimal overtimeHourlyRate, BigDecimal overtimePremiumRate, Integer weeklyLimit,
            String taxMode, String canton, String tariffCode, Boolean monthlySingleEmployment,
            LocalDate birthDate, LocalDate entryDate, Integer taxClass, BigDecimal childAllowance,
            BigDecimal churchRate, BigDecimal healthAdditionalRate, Boolean parent, Integer childrenUnder25,
            Boolean saxony, BigDecimal taxAllowance, BigDecimal taxAddition, BigDecimal taxFactor,
            BigDecimal u1Rate, BigDecimal u2Rate, BigDecimal insolvencyRate) {
        this(country, payType, amount, weeklyHours, overtimeHourlyRate, overtimePremiumRate, weeklyLimit,
                taxMode, canton, tariffCode, monthlySingleEmployment, birthDate, entryDate, taxClass,
                childAllowance, churchRate, healthAdditionalRate, parent, childrenUnder25, saxony,
                taxAllowance, taxAddition, taxFactor, u1Rate, u2Rate, insolvencyRate, null);
    }
    public int thirteenthMonths() { return contract == null ? 0 : contract.paymentMonths(); }
    public LocalDate exitDate() { return contract == null ? null : contract.exitDate(); }
    public boolean hourly() { return "HOURLY".equals(payType); }
    public String currency() { return "CH".equals(country) ? "CHF" : "EUR"; }
}
