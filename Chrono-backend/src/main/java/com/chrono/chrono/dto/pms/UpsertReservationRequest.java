package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.ReservationSource;
import com.chrono.chrono.entities.pms.ReservationStatus;
import com.chrono.chrono.entities.pms.ReservationGuaranteeStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UpsertReservationRequest(
        @NotNull Long propertyId,
        @NotNull Long guestId,
        @NotNull Long roomTypeId,
        Long roomId,
        @NotNull Long ratePlanId,
        @NotNull LocalDate arrivalDate,
        @NotNull LocalDate departureDate,
        @Min(1) @Max(20) int adults,
        @Min(0) @Max(20) int children,
        ReservationStatus status,
        ReservationSource source,
        @Size(max = 2000) String notes,
        ReservationGuaranteeStatus guaranteeStatus,
        LocalDateTime holdUntil
) {
    public UpsertReservationRequest(
            Long propertyId,
            Long guestId,
            Long roomTypeId,
            Long roomId,
            Long ratePlanId,
            LocalDate arrivalDate,
            LocalDate departureDate,
            int adults,
            int children,
            ReservationStatus status,
            ReservationSource source,
            String notes
    ) {
        this(propertyId, guestId, roomTypeId, roomId, ratePlanId, arrivalDate, departureDate,
                adults, children, status, source, notes, null, null);
    }
}
