package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertRateOverrideRequest(
        @NotNull LocalDate stayDate,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @Min(1) @Max(365) int minStay,
        boolean closed,
        boolean closedArrival,
        boolean closedDeparture
) {
}
