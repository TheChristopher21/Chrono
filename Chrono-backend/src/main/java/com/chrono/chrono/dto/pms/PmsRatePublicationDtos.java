package com.chrono.chrono.dto.pms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public final class PmsRatePublicationDtos {
    private PmsRatePublicationDtos() {}
    public record Publish(@NotEmpty @Size(max=100) List<@Valid Target> targets) {}
    public record Target(@NotNull Long propertyId, @NotNull Long roomTypeId, Long targetRatePlanId,
            @NotBlank @Size(max=32) String code, @NotBlank @Size(max=120) String name,
            @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currencyCode,
            @NotNull @DecimalMin("0") BigDecimal nightlyRate, @NotNull @DecimalMin("0") BigDecimal breakfastAmount,
            @NotNull @DecimalMin("0") BigDecimal extraAdultRate, @NotNull @DecimalMin("0") BigDecimal childRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
            @DecimalMin("0") @DecimalMax("100") BigDecimal breakfastVatRate,
            @DecimalMin("0") @DecimalMax("100") BigDecimal policyFeeTaxRate) {}
    public record Published(Long sourceRatePlanId, LocalDateTime publishedAt, List<PublishedTarget> targets) {}
    public record PublishedTarget(Long propertyId, Long ratePlanId, String code, String currencyCode) {}
}
