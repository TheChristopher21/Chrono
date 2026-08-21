package com.chrono.chrono.dto.pms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        Long propertyId,
        LocalDate arrivalDate,
        LocalDate departureDate,
        List<RoomTypeAvailability> roomTypes
) {
    public record RoomTypeAvailability(
            Long roomTypeId,
            String code,
            String name,
            long totalRooms,
            long soldRooms,
            long availableRooms,
            List<RateOption> rates
    ) {
    }

    public record RateOption(
            Long ratePlanId,
            String code,
            String name,
            String currencyCode,
            BigDecimal totalAmount,
            boolean available,
            String restriction
    ) {
    }
}
