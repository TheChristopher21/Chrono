package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertRatePlanRequest(
        @NotNull Long roomTypeId,
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("0.00") BigDecimal nightlyRate,
        @Min(1) @Max(365) int minStay,
        boolean breakfastIncluded,
        boolean refundable,
        Boolean active,
        @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
        Boolean taxIncluded,
        @DecimalMin("0") BigDecimal breakfastAmount,
        @DecimalMin("0") @DecimalMax("100") BigDecimal breakfastVatRate,
        LocalDate validFrom,
        LocalDate validTo,
        LocalDate bookingFrom,
        LocalDate bookingTo,
        @Min(1) @Max(365) Integer maxStay,
        @Min(0) @Max(3650) Integer minAdvanceDays,
        @Min(0) @Max(3650) Integer maxAdvanceDays,
        @Min(1) @Max(100) Integer includedAdults,
        @DecimalMin("0") BigDecimal extraAdultRate,
        @DecimalMin("0") BigDecimal childRate,
        @Min(0) @Max(8760) Integer cancellationDeadlineHours,
        @DecimalMin("0") @DecimalMax("100") BigDecimal cancellationFeePercent,
        @DecimalMin("0") @DecimalMax("100") BigDecimal depositPercent,
        @Min(0) @Max(365) Integer paymentDueDays,
        @Size(max = 2000) String cancellationPolicy,
        @Size(max = 2000) String paymentPolicy,
        @Size(max = 4000) String notes,
        Long organizationId
) {
    public UpsertRatePlanRequest(Long roomTypeId, String code, String name, BigDecimal nightlyRate,
                                 int minStay, boolean breakfastIncluded, boolean refundable, Boolean active) {
        this(roomTypeId, code, name, nightlyRate, minStay, breakfastIncluded, refundable, active,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }
}
