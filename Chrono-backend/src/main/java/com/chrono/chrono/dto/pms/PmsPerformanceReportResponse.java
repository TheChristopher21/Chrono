package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.ReservationSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PmsPerformanceReportResponse(
        Long propertyId,
        String propertyName,
        String currencyCode,
        LocalDate fromDate,
        LocalDate toDateExclusive,
        long availableRoomNights,
        long soldRoomNights,
        BigDecimal occupancyPercent,
        BigDecimal roomRevenue,
        BigDecimal adr,
        BigDecimal revPar,
        long arrivals,
        long cancellations,
        long noShows,
        String methodology,
        List<DailyPerformance> daily,
        List<SourcePerformance> sources
) {
    public record DailyPerformance(
            LocalDate date,
            long availableRooms,
            long soldRooms,
            BigDecimal occupancyPercent,
            BigDecimal roomRevenue,
            BigDecimal adr,
            BigDecimal revPar
    ) {
    }

    public record SourcePerformance(
            ReservationSource source,
            long soldRoomNights,
            BigDecimal roomRevenue,
            BigDecimal sharePercent
    ) {
    }
}
