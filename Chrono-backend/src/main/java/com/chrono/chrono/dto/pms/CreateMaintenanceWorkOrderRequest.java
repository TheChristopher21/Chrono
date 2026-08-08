package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.MaintenancePriority;
import com.chrono.chrono.entities.pms.RoomBlockType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateMaintenanceWorkOrderRequest(
        @NotNull Long roomId,
        @NotBlank @Size(max = 180) String title,
        @Size(max = 2000) String description,
        @NotNull MaintenancePriority priority,
        @Size(max = 120) String assignedTo,
        LocalDate dueDate,
        boolean blockRoom,
        RoomBlockType blockType,
        LocalDate blockStartDate,
        LocalDate blockEndDate
) {
}
