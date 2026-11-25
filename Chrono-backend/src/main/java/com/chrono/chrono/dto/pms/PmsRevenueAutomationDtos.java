package com.chrono.chrono.dto.pms;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public final class PmsRevenueAutomationDtos {
 private PmsRevenueAutomationDtos() {}
 public record Rule(Long ratePlanId,BigDecimal minPrice,BigDecimal maxPrice,BigDecimal maxChangePercent,int lowOccupancy,int highOccupancy,BigDecimal adjustmentPercent) {}
 public record Config(Long propertyId,long version,boolean snapshotsEnabled,boolean pricingEnabled,int horizonDays,int minSamples,List<Rule> rules) {}
 public record Observation(LocalDate date,long roomNights,BigDecimal netRoomRevenue) {}
 public record Prediction(LocalDate date,long onBooks,Long expectedRoomNights,Long lowerRoomNights,Long upperRoomNights,BigDecimal expectedNetRoomRevenue,int samples,int seasonalSamples,int pickupSamples,String method) {}
 public record Suggestion(Long ratePlanId,String rateName,LocalDate stayDate,BigDecimal currentPrice,BigDecimal proposedPrice,String reason,boolean automaticEligible) {}
 public record Forecast(Long propertyId,String currencyCode,LocalDate businessDate,long capacity,int observedDays,String model,List<Prediction> days,List<Suggestion> suggestions) {}
 public record Apply(Long ratePlanId,LocalDate stayDate,BigDecimal expectedCurrentPrice,BigDecimal proposedPrice) {}
}
