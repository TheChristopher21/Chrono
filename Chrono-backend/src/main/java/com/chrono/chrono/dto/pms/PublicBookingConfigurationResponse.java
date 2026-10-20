package com.chrono.chrono.dto.pms;

public record PublicBookingConfigurationResponse(
        String propertyCode,
        String hotelName,
        String city,
        String currencyCode,
        String termsUrl,
        String privacyUrl,
        boolean requireGuarantee
) {}
