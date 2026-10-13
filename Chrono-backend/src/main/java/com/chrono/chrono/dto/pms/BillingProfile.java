package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.*;

/** Postal layout and billing instructions; country never determines the hotel's tax jurisdiction. */
public record BillingProfile(
        @Size(max = 180) String legalName,
        @Size(max = 180) String attention,
        @Size(max = 180) String addressLine1,
        @Size(max = 180) String addressLine2,
        @Size(max = 20) String postalCode,
        @Size(max = 120) String city,
        @Size(max = 100) String region,
        @Pattern(regexp = "^([A-Za-z]{2})?$") String countryCode,
        @Size(max = 80) String vatNumber,
        @Email @Size(max = 190) String billingEmail,
        @Size(max = 120) String reference,
        @Size(max = 120) String costCenter,
        @Pattern(regexp = "^(COMPANY_FIRST|PERSON_FIRST)$") String recipientOrder,
        @Pattern(regexp = "^(POSTAL_CITY|CITY_REGION_POSTAL)$") String addressFormat,
        @Size(max = 2000) String footer
) { }
