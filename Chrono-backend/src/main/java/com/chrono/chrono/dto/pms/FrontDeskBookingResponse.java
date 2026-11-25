package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.GuestRegistrationStatus;
import com.chrono.chrono.entities.pms.ReservationStatus;

import java.math.BigDecimal;
import java.util.List;

public record FrontDeskBookingResponse(
        Long guestId,
        Long reservationId,
        Long folioId,
        String confirmationCode,
        BigDecimal totalAmount,
        BigDecimal balance,
        GuestRegistrationStatus registrationStatus,
        ReservationStatus reservationStatus,
        boolean checkInReady,
        List<String> checkInBlockers,
        PmsOperationsResponse operations
) {
}
