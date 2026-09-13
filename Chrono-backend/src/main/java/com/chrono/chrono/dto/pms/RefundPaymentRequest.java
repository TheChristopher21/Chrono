package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundPaymentRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Size(max = 500) String reason,
        @Size(max = 80) String requestId,
        Long cashShiftId
) {
    public RefundPaymentRequest(BigDecimal amount, String reason) { this(amount, reason, java.util.UUID.randomUUID().toString(), null); }
}
