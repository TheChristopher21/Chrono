package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertRoomTypeRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @Min(1) @Max(20) int baseOccupancy,
        @Min(1) @Max(20) int maxOccupancy,
        @Min(1) @Max(20) int bedCount,
        @Size(max = 60) String bedType,
        @Min(0) int sortOrder,
        Boolean active
) {
}
