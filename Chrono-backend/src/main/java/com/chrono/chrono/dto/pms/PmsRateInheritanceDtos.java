package com.chrono.chrono.dto.pms;
import java.math.BigDecimal;
import java.time.*;
public final class PmsRateInheritanceDtos {
 private PmsRateInheritanceDtos() {}
 public record Input(Long sourceRatePlanId,Long targetRatePlanId,Long expectedVersion,boolean enabled,boolean frozen,boolean inheritPolicy,BigDecimal manualFxRate,LocalDate fxDate,LocalDate fxValidUntil,String fxReference,BigDecimal adjustmentPercent,BigDecimal minPrice,BigDecimal maxPrice) {}
 public record View(Long id,long version,Long sourceRatePlanId,String sourceRateName,String sourceHotel,String sourceCurrency,Long targetRatePlanId,String targetRateName,String targetCurrency,boolean enabled,boolean frozen,boolean inheritPolicy,BigDecimal manualFxRate,LocalDate fxDate,LocalDate fxValidUntil,String fxReference,BigDecimal adjustmentPercent,BigDecimal minPrice,BigDecimal maxPrice,BigDecimal previewPrice,LocalDateTime lastAppliedAt,String lastResult) {}
}
