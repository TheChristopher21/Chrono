package com.chrono.chrono.dto.pms;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
public final class PmsRevenuePlanningDtos {
 private PmsRevenuePlanningDtos() {}
 public record BudgetInput(@NotNull @DecimalMin("0") BigDecimal netRoomRevenue,@Min(0) long roomNights,Long expectedVersion) {}
 public record BudgetView(Long id,long version,LocalDate monthStart,BigDecimal netRoomRevenue,long roomNights,LocalDateTime updatedAt,String updatedBy) {}
 public record SnapshotView(Long id,LocalDate asOfDate,LocalDate toExclusive,LocalDateTime capturedAt,String capturedBy) {}
 public record Day(LocalDate date,long roomNights,BigDecimal netRoomRevenue,long unknownRevenueRoomNights,Long previousRoomNights,
                   BigDecimal previousNetRoomRevenue,Long pickupRoomNights,BigDecimal pickupNetRoomRevenue) {}
 public record Month(LocalDate monthStart,int coveredDays,int daysInMonth,long roomNights,BigDecimal netRoomRevenue,
                     Long previousRoomNights,BigDecimal previousNetRoomRevenue,Long pickupRoomNights,BigDecimal pickupNetRoomRevenue,
                     BigDecimal proratedBudgetRoomNights,BigDecimal proratedBudgetNetRoomRevenue,BigDecimal budgetRevenueVariance,BudgetView budget) {}
 public record Summary(long roomNights,BigDecimal netRoomRevenue,Long previousRoomNights,BigDecimal previousNetRoomRevenue,
                       Long pickupRoomNights,BigDecimal pickupNetRoomRevenue,long unknownRevenueRoomNights,int comparisonCoverageDays,int requestedDays) {}
 public record Report(Long propertyId,String currencyCode,LocalDate businessDate,LocalDate from,LocalDate toExclusive,
                      SnapshotView comparisonSnapshot,List<SnapshotView> snapshots,Summary summary,List<Day> days,List<Month> months) {}
}
