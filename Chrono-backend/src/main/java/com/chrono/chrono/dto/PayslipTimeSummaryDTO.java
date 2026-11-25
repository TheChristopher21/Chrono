package com.chrono.chrono.dto;

public record PayslipTimeSummaryDTO(
        Integer currentBalanceMinutes,
        Integer workedMinutes,
        Integer expectedMinutes,
        Integer differenceMinutes
) {}
