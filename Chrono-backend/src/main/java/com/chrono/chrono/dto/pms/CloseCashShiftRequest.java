package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CloseCashShiftRequest(
        @NotNull @DecimalMin("0.00") BigDecimal actualCash,
        @Size(max = 500) String notes,
        Long cashShiftId
) {
    public CloseCashShiftRequest(BigDecimal actualCash, String notes) { this(actualCash, notes, null); }
}
