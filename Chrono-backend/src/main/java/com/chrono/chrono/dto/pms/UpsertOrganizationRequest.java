package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertOrganizationRequest(
        @NotNull OrganizationType type,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 40) String vatNumber,
        @Size(max = 180) String addressLine1,
        @Size(max = 20) String postalCode,
        @Size(max = 120) String city,
        @Size(min = 2, max = 2) String countryCode,
        @Email @Size(max = 190) String email,
        @Size(max = 60) String phone,
        @Email @Size(max = 190) String billingEmail,
        @Min(0) @Max(365) int paymentTermsDays,
        @Size(max = 1000) String notes,
        boolean active
) {
}
