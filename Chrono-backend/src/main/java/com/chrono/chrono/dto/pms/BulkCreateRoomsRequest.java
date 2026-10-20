package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.RoomOperationalStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BulkCreateRoomsRequest(
        @NotNull Long roomTypeId,
        @NotBlank @Size(max = 40) String startNumber,
        @Min(1) @Max(200) int count,
        @Size(max = 120) String namePrefix,
        @Size(max = 40) String floor,
        @Size(max = 80) String housekeepingSection,
        @Size(max = 1000) String features,
        RoomOperationalStatus operationalStatus,
        Boolean active
) {
}
