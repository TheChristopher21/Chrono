package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;
import java.util.List;
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
        boolean active,
        Boolean masterRecord,
        Long parentOrganizationId,
        @Email @Size(max = 190) String privateEmail,
        @Email @Size(max = 190) String businessEmail,
        @Size(max = 20) List<@Email @Size(max = 190) String> additionalEmails,
        @Valid @Size(max = 100) List<OrganizationContact> contacts,
        @Valid BillingProfile billingProfile
) {
    public UpsertOrganizationRequest(OrganizationType type, String name, String vatNumber, String addressLine1,
        String postalCode, String city, String countryCode, String email, String phone, String billingEmail,
        int paymentTermsDays, String notes, boolean active, Boolean masterRecord, Long parentOrganizationId) {
        this(type,name,vatNumber,addressLine1,postalCode,city,countryCode,email,phone,billingEmail,paymentTermsDays,
            notes,active,masterRecord,parentOrganizationId,null,null,null,null,null);
    }
    public UpsertOrganizationRequest(
            OrganizationType type, String name, String vatNumber, String addressLine1, String postalCode,
            String city, String countryCode, String email, String phone, String billingEmail,
            int paymentTermsDays, String notes, boolean active
    ) {
        this(type, name, vatNumber, addressLine1, postalCode, city, countryCode, email, phone,
                billingEmail, paymentTermsDays, notes, active, false, null, null, null, null, null, null);
    }
}
