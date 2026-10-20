package com.chrono.chrono.services.payroll;

import java.time.LocalDate;

/** Contractual choices, not statutory defaults. Null on old versions means not configured. */
public record PayrollContract(LocalDate exitDate, String salaryProration, String pensionProration,
                              String thirteenthFrequency, java.math.BigDecimal germanPreviousEmploymentGross) {
    public int paymentMonths() {
        return switch (thirteenthFrequency == null ? "NONE" : thirteenthFrequency) {
            case "MONTHLY" -> 1; case "QUARTERLY" -> 3; case "SEMIANNUAL" -> 6; case "ANNUAL" -> 12;
            default -> 0;
        };
    }
}
