package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.ReservationGuaranteeStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ReservationLifecycleRequest(
        @Size(max = 500) String reason,
        LocalDateTime holdUntil,
        ReservationGuaranteeStatus guaranteeStatus
) {
}
