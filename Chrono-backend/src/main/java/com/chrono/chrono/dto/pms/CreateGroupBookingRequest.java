package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.GroupBookingStatus;
import com.chrono.chrono.entities.pms.ReservationSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.AssertTrue;
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
        @NotNull @Size(max = 1000) List<@Valid RoomingEntry> rooms
) {
    public record RoomingEntry(
            @NotNull Long guestId,
            @NotNull Long roomTypeId,
            Long roomId,
            @NotNull Long ratePlanId,
            @Min(1) @Max(20) int adults,
            @Min(0) @Max(20) int children,
            ReservationSource source,
            @Size(max = 2000) String notes,
            @Size(max = 20) List<@Min(0) @Max(17) Integer> childAges,
            LocalDate arrivalDate,
            LocalDate departureDate
    ) {
        public RoomingEntry(Long guestId, Long roomTypeId, Long roomId, Long ratePlanId,
                            int adults, int children, ReservationSource source, String notes) {
            this(guestId, roomTypeId, roomId, ratePlanId, adults, children, source, notes, null, null, null);
        }

        public RoomingEntry(Long guestId, Long roomTypeId, Long roomId, Long ratePlanId,
                            int adults, int children, ReservationSource source, String notes, List<Integer> childAges) {
            this(guestId,roomTypeId,roomId,ratePlanId,adults,children,source,notes,childAges,null,null);
        }

        @AssertTrue(message = "Wenn Kinderalter angegeben sind, muss für jedes Kind genau ein Alter erfasst sein.")
        public boolean isChildAgeCountValid() {
            return childAges == null || childAges.isEmpty() || childAges.size() == children;
        }
    }
}
