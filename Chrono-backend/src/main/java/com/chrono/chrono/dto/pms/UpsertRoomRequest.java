package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.RoomOperationalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertRoomRequest(
        @NotNull Long roomTypeId,
        @NotBlank @Size(max = 40) String number,
        @Size(max = 120) String name,
        @Size(max = 40) String floor,
        @Size(max = 80) String housekeepingSection,
        RoomOperationalStatus operationalStatus,
        Boolean active
) {
}
