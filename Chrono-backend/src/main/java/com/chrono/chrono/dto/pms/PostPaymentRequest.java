package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PostPaymentRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotNull PaymentMethod method,
        @Size(max = 120) String reference,
        Long cashShiftId
) {
    public PostPaymentRequest(BigDecimal amount, PaymentMethod method, String reference) { this(amount, method, reference, null); }
}
