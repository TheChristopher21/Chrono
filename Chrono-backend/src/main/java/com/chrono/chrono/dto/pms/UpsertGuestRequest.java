package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

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
        Long organizationId
) {
    public UpsertGuestRequest(
            String firstName, String lastName, String email, String phone, LocalDate dateOfBirth,
            String nationalityCode, String languageCode, String notes, Boolean vip
    ) {
        this(firstName, lastName, email, phone, dateOfBirth, nationalityCode, languageCode, notes, vip,
                null, null, null, null, null, null, null);
    }
}
