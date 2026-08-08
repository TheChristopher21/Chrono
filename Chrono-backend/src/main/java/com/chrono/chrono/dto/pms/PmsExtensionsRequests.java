package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PmsExtensionsRequests {
    private PmsExtensionsRequests() {}

    public record BookingSettings(
            @NotBlank @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{1,118}[a-z0-9])?$") String publicSlug,
            boolean enabled,
            boolean requireGuarantee,
            @Size(max = 500) String termsUrl,
            @Size(max = 500) String privacyUrl,
            @Size(max = 1000) String confirmationMessage
    ) {}

    public record TourismTaxRuleRequest(
            boolean enabled,
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin("0.00") BigDecimal adultRate,
            @NotNull @DecimalMin("0.00") BigDecimal childRate,
            @Min(0) @Max(21) int childFreeUnder,
            @Min(1) @Max(365) Integer maximumNights
    ) {}

    public record PosLine(
            @NotBlank @Size(max = 240) String description,
            @NotNull @DecimalMin("0.01") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal taxRate
    ) {}

    public record CreatePosTicket(
            Long folioId,
            @NotBlank @Size(max = 32) String outletCode,
            @Size(max = 60) String tableReference,
            @NotNull LocalDate serviceDate,
            PaymentMethod paymentMethod,
            @NotEmpty @Size(max = 100) List<@Valid PosLine> lines
    ) {}

    public record IssueAccessCredential(
            @NotNull Long reservationId,
            @NotBlank @Size(max = 50) String providerCode,
            @NotBlank @Size(max = 160) String externalReference,
            @NotNull LocalDateTime validFrom,
            @NotNull LocalDateTime validUntil
    ) {}

    public record PostTourismTax(
            @NotNull Long reservationId,
            @Min(0) @Max(20) int chargeableChildren
    ) {}

    public record PublicBooking(
            @NotNull LocalDate arrivalDate,
            @NotNull LocalDate departureDate,
            @NotNull Long ratePlanId,
            @Min(1) @Max(20) int adults,
            @Min(0) @Max(20) int children,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Email @NotBlank @Size(max = 190) String email,
            @Size(max = 60) String phone,
            @AssertTrue boolean termsAccepted,
            @AssertTrue boolean privacyAccepted
    ) {}

    public record VerifyPublicBooking(
            @NotBlank @Size(min = 32, max = 200) String token
    ) {}

    public record MigrationReservation(
            @NotBlank @Size(max = 100) String externalReference,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Email @Size(max = 190) String email,
            @Size(max = 60) String phone,
            @NotNull Long roomTypeId,
            @NotNull Long ratePlanId,
            @NotNull LocalDate arrivalDate,
            @NotNull LocalDate departureDate,
            @Min(1) @Max(20) int adults,
            @Min(0) @Max(20) int children,
            @NotNull @DecimalMin("0.00") BigDecimal expectedGrossAmount,
            @NotNull @DecimalMin("0.00") BigDecimal depositAmount
    ) {}

    public record MigrationImport(
            @NotBlank @Size(max = 120) String idempotencyKey,
            @NotBlank @Size(max = 100) String sourceSystem,
            @NotEmpty @Size(max = 5000) List<@Valid MigrationReservation> reservations
    ) {}
}
