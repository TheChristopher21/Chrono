package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.GroupBookingStatus;
import com.chrono.chrono.entities.pms.ReservationSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateGroupBookingRequest(
        @NotNull Long propertyId,
        @NotNull Long contactGuestId,
        Long organizationId,
        @NotBlank @Size(max = 40) String groupCode,
        @NotBlank @Size(max = 180) String name,
        @NotNull LocalDate arrivalDate,
        @NotNull LocalDate departureDate,
        GroupBookingStatus status,
        @Size(max = 2000) String notes,
        @NotEmpty @Size(max = 100) List<@Valid RoomingEntry> rooms
) {
    public record RoomingEntry(
            @NotNull Long guestId,
            @NotNull Long roomTypeId,
            Long roomId,
            @NotNull Long ratePlanId,
            @Min(1) @Max(20) int adults,
            @Min(0) @Max(20) int children,
            ReservationSource source,
            @Size(max = 2000) String notes
    ) {
    }
}
