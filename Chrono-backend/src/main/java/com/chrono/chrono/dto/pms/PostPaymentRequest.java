package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PostPaymentRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull PaymentMethod method,
        @Size(max = 120) String reference
) {
}
