package com.chrono.chrono.services.pms;

public record PublicBookingVerificationRequested(
        String recipient,
        String hotelName,
        String publicSlug,
        String confirmationCode,
        String token
) {}
