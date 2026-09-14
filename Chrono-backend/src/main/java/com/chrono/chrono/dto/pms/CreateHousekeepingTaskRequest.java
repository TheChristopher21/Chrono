package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.HousekeepingTaskType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateHousekeepingTaskRequest(
        @NotNull Long roomId,
        @NotNull LocalDate serviceDate,
        @NotNull HousekeepingTaskType type,
        @Min(0) @Max(100) int priority,
        @Min(1) @Max(1440) int estimatedMinutes,
        @Size(max = 1000) String notes,
        @Size(max = 120) String assignedTo
) {
}
