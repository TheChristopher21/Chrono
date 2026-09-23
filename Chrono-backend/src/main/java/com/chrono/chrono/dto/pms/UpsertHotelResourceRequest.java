package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.HotelResourceType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpsertHotelResourceRequest(
        @NotNull HotelResourceType type,
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 160) String location,
        @Min(1) int capacity,
        @NotNull @DecimalMin("0.00") BigDecimal hourlyRate,
        @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        boolean active
) {
}
