package com.chrono.chrono.dto;

import java.time.LocalDate;
import java.util.List;

/** Read-only evidence. Passing these checks does not certify tax calculations. */
public record PayrollReviewDTO(Long userId, LocalDate start, LocalDate end,
                               Integer workedMinutes, Integer expectedMinutes, Integer differenceMinutes,
                               List<DailyTimeSummaryDTO> days, List<Issue> issues,
                               boolean ready, boolean taxCalculationVerified) {
    public record Issue(String code, String severity, String message, LocalDate date, Long payslipId) {}
}
