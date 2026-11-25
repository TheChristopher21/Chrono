package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MoveReservationRoomRequest(
        @NotNull Long roomId,
        @Size(max = 500) String reason,
        java.time.LocalDate effectiveDate,
        Long ratePlanId
) {
    public MoveReservationRoomRequest(Long roomId, String reason) { this(roomId, reason, null, null); }
}
