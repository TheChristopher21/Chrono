package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpsertRatePlanRequest(
        @NotNull Long roomTypeId,
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("0.00") BigDecimal nightlyRate,
        @Min(1) @Max(365) int minStay,
        boolean breakfastIncluded,
        boolean refundable,
        Boolean active
) {
}
