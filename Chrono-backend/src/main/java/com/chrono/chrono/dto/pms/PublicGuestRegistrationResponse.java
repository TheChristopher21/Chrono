package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.GuestRegistrationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PublicGuestRegistrationResponse(
        Long registrationId,
        GuestRegistrationStatus status,
        String hotelName,
        String guestName,
        String confirmationCode,
        LocalDate arrivalDate,
        LocalDate departureDate,
        String ruleCode,
        int ruleVersion,
        List<String> requiredFields,
        LocalDateTime expiresAt
) {
}
