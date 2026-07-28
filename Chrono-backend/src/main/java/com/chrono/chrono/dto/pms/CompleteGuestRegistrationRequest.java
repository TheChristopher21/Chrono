package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteGuestRegistrationRequest(
        @NotBlank @Size(max = 180) String addressLine,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(min = 2, max = 2) String countryCode,
        @NotBlank @Size(min = 2, max = 2) String nationalityCode,
        @NotBlank @Size(min = 4, max = 120) String documentNumber,
        @Size(max = 32) String vehiclePlate,
        @NotBlank @Size(max = 180) String signatureName,
        @AssertTrue boolean privacyConsent
) {
}
