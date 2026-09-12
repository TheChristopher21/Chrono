package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;

public record UpsertGuestRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 190) String email,
        @Size(max = 60) String phone,
        LocalDate dateOfBirth,
        @Pattern(regexp = "^[A-Za-z]{2}$") String nationalityCode,
        @Pattern(regexp = "^[A-Za-z]{2,8}$") String languageCode,
        @Size(max = 1000) String notes,
        Boolean vip,
        @Size(max = 180) String addressLine1,
        @Size(max = 20) String postalCode,
        @Size(max = 120) String city,
        @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
        @Size(max = 40) String vehiclePlate,
        @Size(max = 1000) String roomPreferences,
        Long organizationId,
        @Email @Size(max = 190) String privateEmail,
        @Email @Size(max = 190) String businessEmail,
        @Size(max = 20) List<@Email @Size(max = 190) String> additionalEmails,
        @Size(max = 1000) String dietaryNotes,
        @Size(max = 80) String vatNumber,
        @Size(max = 36) String organizationContactId,
        Boolean billingOverride,
        @Valid BillingProfile billingProfile
) {
    public UpsertGuestRequest(String firstName, String lastName, String email, String phone, LocalDate dateOfBirth,
        String nationalityCode, String languageCode, String notes, Boolean vip, String addressLine1,
        String postalCode, String city, String countryCode, String vehiclePlate, String roomPreferences, Long organizationId) {
        this(firstName,lastName,email,phone,dateOfBirth,nationalityCode,languageCode,notes,vip,addressLine1,
            postalCode,city,countryCode,vehiclePlate,roomPreferences,organizationId,null,null,null,null,null,null,null,null);
    }
    public UpsertGuestRequest(
            String firstName, String lastName, String email, String phone, LocalDate dateOfBirth,
            String nationalityCode, String languageCode, String notes, Boolean vip
    ) {
        this(firstName, lastName, email, phone, dateOfBirth, nationalityCode, languageCode, notes, vip,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
