package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.ReservationGuaranteeStatus;
import com.chrono.chrono.entities.pms.ReservationSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

public record CreateFrontDeskBookingRequest(
        Long existingGuestId,
        @Valid UpsertGuestRequest newGuest,
        @NotNull Long roomTypeId,
        Long roomId,
        @NotNull Long ratePlanId,
        @NotNull LocalDate arrivalDate,
        @NotNull LocalDate departureDate,
        @Min(1) @Max(20) int adults,
        @Min(0) @Max(20) int children,
        @NotNull ReservationSource source,
        ReservationGuaranteeStatus guaranteeStatus,
        @Size(max = 2000) String notes,
        @Valid CompleteGuestRegistrationRequest registration,
        boolean checkInNow
) {
    private static final Set<ReservationSource> FRONT_DESK_SOURCES = EnumSet.of(
            ReservationSource.DIRECT,
            ReservationSource.PHONE,
            ReservationSource.EMAIL,
            ReservationSource.WALK_IN
    );

    @AssertTrue(message = "Genau ein bestehender oder neuer Gast muss angegeben werden.")
    public boolean isGuestSelectionValid() {
        return (existingGuestId == null) != (newGuest == null);
    }

    @AssertTrue(message = "Für einen neuen Gast muss eine E-Mail-Adresse oder Telefonnummer angegeben werden.")
    public boolean isNewGuestContactValid() {
        return newGuest == null || hasText(newGuest.email()) || hasText(newGuest.phone());
    }

    @AssertTrue(message = "Die Buchungsquelle ist für eine Rezeptionsbuchung nicht zulässig.")
    public boolean isSourceValid() {
        return source == null || FRONT_DESK_SOURCES.contains(source);
    }

    @AssertTrue(message = "WALK_IN und sofortiger Check-in müssen gemeinsam gewählt werden.")
    public boolean isWalkInConsistentWithImmediateCheckIn() {
        return source == null || (source == ReservationSource.WALK_IN) == checkInNow;
    }

    @AssertTrue(message = "Für einen direkten Check-in müssen die Meldedaten vollständig vorliegen.")
    public boolean isRegistrationPresentForCheckIn() {
        return !checkInNow || registration != null;
    }

    @AssertTrue(message = "Für einen direkten Check-in muss ein Zimmer zugewiesen sein.")
    public boolean isRoomAssignedForCheckIn() {
        return !checkInNow || roomId != null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
