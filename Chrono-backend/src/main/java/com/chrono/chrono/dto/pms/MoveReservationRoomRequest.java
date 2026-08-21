package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MoveReservationRoomRequest(
        @NotNull Long roomId,
        @Size(max = 500) String reason
) {
}
