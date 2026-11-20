package com.chrono.chrono.services.payroll;

import java.time.LocalDate;

/** Contractually separate insurance allocation; the limit remains an annual 360-day amount. */
public record PayrollKtgPool(String key, LocalDate from, LocalDate through) {}
