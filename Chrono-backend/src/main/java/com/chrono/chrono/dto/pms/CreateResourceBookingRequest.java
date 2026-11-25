package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.ResourceBookingStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateResourceBookingRequest(
        @NotNull Long resourceId,
        Long groupBookingId,
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 180) String organizerName,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @Min(1) int attendees,
        @NotNull ResourceBookingStatus status,
        @NotNull @DecimalMin("0.00") BigDecimal totalAmount,
        @Size(max = 2000) String notes
) {
}
