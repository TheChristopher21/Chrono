package com.chrono.chrono.dto.pms;
import com.chrono.chrono.entities.pms.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
public final class PmsHousekeepingDtos {
    private PmsHousekeepingDtos() {}
    public record Create(@NotNull @Positive Long roomId, @NotNull LocalDate serviceDate,
            @NotNull HousekeepingWorkType workType, @NotNull HousekeepingTaskType type,
            @Min(0) @Max(100) int priority, @Min(1) @Max(1440) int estimatedMinutes,
            @Size(max=1000) String notes, @Size(max=120) String assignedTo) {}
    public record Update(@NotNull @PositiveOrZero Long version, @NotNull HousekeepingWorkStatus workStatus,
            @Min(0) @Max(100) int priority, @Min(1) @Max(1440) int estimatedMinutes,
            @Size(max=1000) String notes, @Size(max=120) String assignedTo) {}
    public record Task(Long id, long version, Long roomId, String roomNumber, LocalDate serviceDate,
            HousekeepingWorkType workType, HousekeepingWorkStatus workStatus, HousekeepingTaskType type,
            int priority, int estimatedMinutes, String notes, String assignedTo, LocalDateTime completedAt) {}
    public record Event(Long id, HousekeepingWorkStatus fromStatus, HousekeepingWorkStatus toStatus,
            String assignedTo, String notes, String actor, LocalDateTime createdAt) {}
    public record History(List<Event> items, int page, int size, long totalElements, boolean hasNext) {}
    public record BatchCreate(@NotNull @Size(min=1,max=100) List<@NotNull @Positive Long> roomIds,
            @NotNull LocalDate from, @NotNull LocalDate toExclusive, @NotNull HousekeepingWorkType workType,
            @Size(max=120) String assignedTo, @Size(max=1000) String notes,
            @Min(0) @Max(100) int priority, @Min(1) @Max(1440) int estimatedMinutes) {}
    public record BatchResult(int created, int alreadyExisting) {}
}
