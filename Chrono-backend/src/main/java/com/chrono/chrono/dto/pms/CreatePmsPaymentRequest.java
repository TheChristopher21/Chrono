package com.chrono.chrono.dto.pms;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record CreatePmsPaymentRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{8,80}") String requestId,
                                     @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
                                     @NotBlank @Pattern(regexp = "PAYMENT|AUTHORIZATION") String kind) {}
