package com.chrono.chrono.dto.pms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PmsPortfolioResponse(
        LocalDate businessDate,
        long properties,
        long operationalRooms,
        long availableRooms,
        long soldRooms,
        BigDecimal occupancyPercent,
        long arrivals,
        long departures,
        List<PropertySummary> hotels
) {
    public record PropertySummary(
            Long propertyId,
            String code,
            String name,
            String city,
            String timezone,
            String currencyCode,
            long operationalRooms,
            long availableRooms,
            long soldRooms,
            BigDecimal occupancyPercent,
            long arrivals,
            long departures
    ) {
    }
}
