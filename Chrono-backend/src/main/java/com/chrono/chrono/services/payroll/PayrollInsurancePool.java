package com.chrono.chrono.services.payroll;
import java.time.LocalDate;
/** Insurer/code identity; statutory ceiling consumption may not cross this boundary. */
public record PayrollInsurancePool(String key,LocalDate from,LocalDate through) {}
