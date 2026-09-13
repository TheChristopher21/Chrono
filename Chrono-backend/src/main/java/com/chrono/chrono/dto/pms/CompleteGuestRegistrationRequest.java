package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteGuestRegistrationRequest(
        @Size(max = 180) String addressLine,
        @Size(max = 20) String postalCode,
        @Size(max = 120) String city,
        @jakarta.validation.constraints.Pattern(regexp="^([A-Za-z]{2})?$") String countryCode,
        @jakarta.validation.constraints.Pattern(regexp="^([A-Za-z]{2})?$") String nationalityCode,
        @Size(max = 120) String documentNumber,
        @Size(max = 32) String vehiclePlate,
        @Size(max = 180) String signatureName,
        boolean privacyConsent,
        String acknowledgedRuleCode,
        Integer acknowledgedRuleVersion
) {
}
