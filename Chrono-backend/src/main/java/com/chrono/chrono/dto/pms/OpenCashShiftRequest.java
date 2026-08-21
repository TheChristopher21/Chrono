package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OpenCashShiftRequest(
        @NotNull @DecimalMin("0.00") BigDecimal openingFloat,
        @Size(max = 500) String notes
) {
}
