package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record UpsertHotelPropertyRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 180) String legalName,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{2}") String countryCode,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}") String currencyCode,
        @NotBlank @Size(max = 64) String timezone,
        @Size(max = 180) String addressLine1,
        @Size(max = 20) String postalCode,
        @Size(max = 120) String city,
        @Size(max = 60) String phone,
        @Email @Size(max = 190) String email,
        @NotNull LocalTime checkInTime,
        @NotNull LocalTime checkOutTime,
        Boolean active,
        @Size(max = 80) String taxNumber,
        @Size(max = 40) String taxRegistrationLabel,
        @Size(max = 100) String registrationNumber,
        @Size(max = 180) String addressLine2,
        @Size(max = 100) String region,
        @Size(max = 2000) String invoiceFooter,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,24}") String invoicePrefix,
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(365) Integer invoiceDueDays
) {
    public UpsertHotelPropertyRequest(String code, String name, String legalName, String countryCode,
            String currencyCode, String timezone, String addressLine1, String postalCode, String city,
            String phone, String email, LocalTime checkInTime, LocalTime checkOutTime, Boolean active) {
        this(code, name, legalName, countryCode, currencyCode, timezone, addressLine1, postalCode, city,
                phone, email, checkInTime, checkOutTime, active, null, null, null, null, null, null, null, null);
    }
}
