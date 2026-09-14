package com.chrono.chrono.config;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.services.pms.PmsPropertyAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PmsAccessResponseAdviceTest {
    ObjectMapper mapper = new ObjectMapper();
    PmsAccessResponseAdvice advice = new PmsAccessResponseAdvice(mock(PmsAccessPolicy.class), mapper);
    @Test void housekeepingCannotReadGuestFinancialOrRateDataFromBroadOperations() throws Exception {
        var actor = new PmsPropertyAccessService.Access(7L, 10L, false, Map.of(5L, Map.of("HOUSEKEEPING", "MANAGE")));
        ObjectNode payload = (ObjectNode) mapper.readTree("""
                {"guests":[{"email":"private@example.com"}],"organizations":[{"name":"Private"}],
                "folios":[{"balance":123}],"cashShift":{"amount":123},"metrics":{"openBalance":123,"totalRooms":50},
                "ratePlans":[{"price":100}],"reservations":[{"guestName":"Private"}],
                "housekeepingTasks":[{"roomId":1}],"rooms":[{"currentReservation":{"id":2,"guestName":"Private","guestEmail":"private@example.com","totalAmount":500,"arrivalDate":"2026-09-12"}}]}
                """);
        advice.filterPayload(payload, actor, 5L, PmsOperationsResponse.class);
        assertThat(payload.path("guests").isEmpty()).isTrue();
        assertThat(payload.path("folios").isEmpty()).isTrue();
        assertThat(payload.path("cashShift").isNull()).isTrue();
        assertThat(payload.path("metrics").path("openBalance").isNull()).isTrue();
        assertThat(payload.path("housekeepingTasks").size()).isEqualTo(1);
        assertThat(payload.toString()).doesNotContain("private@example.com", "Private", "totalAmount");
    }
    @Test void portfolioTotalsExcludeHotelsWithoutReportingGrant() {
        var actor = new PmsPropertyAccessService.Access(7L, 10L, false, Map.of(5L, Map.of("REPORTS", "VIEW"), 6L, Map.of("HOUSEKEEPING", "MANAGE")));
        var first = new PmsPortfolioResponse.PropertySummary(5L, "A", "Hotel A", "Town", "UTC", "EUR", 10, 10, 5, BigDecimal.valueOf(50), 2, 1);
        var second = new PmsPortfolioResponse.PropertySummary(6L, "B", "Hotel B", "Town", "UTC", "EUR", 100, 100, 100, BigDecimal.valueOf(100), 90, 50);
        var input = new PmsPortfolioResponse(LocalDate.now(), 2, 110, 110, 105, BigDecimal.valueOf(95), 92, 51, List.of(first, second));
        var result = (PmsPortfolioResponse) advice.filterPortfolio(input, actor);
        assertThat(result.hotels()).containsExactly(first);
        assertThat(result.operationalRooms()).isEqualTo(10);
        assertThat(result.arrivals()).isEqualTo(2);
        assertThat(result.occupancyPercent()).isEqualByComparingTo("50");
    }
    @Test void receptionBookingResponseCannotBypassNestedModuleFiltering() throws Exception {
        var actor = new PmsPropertyAccessService.Access(7L,10L,false,Map.of(5L,Map.of("FRONT_DESK","MANAGE")));
        ObjectNode payload=(ObjectNode)mapper.readTree("""
            {"balance":123,"reservationId":8,"operations":{"reservations":[{"id":8}],"guests":[{"email":"hidden"}],"folios":[{"balance":123}],"housekeepingTasks":[{"notes":"hidden"}],"cashShift":{"amount":99}}}
            """);
        advice.filterPayload(payload,actor,5L,FrontDeskBookingResponse.class);
        assertThat(payload.path("balance").isNull()).isTrue();
        assertThat(payload.path("operations").path("reservations").size()).isEqualTo(1);
        assertThat(payload.path("operations").path("folios").isEmpty()).isTrue();
        assertThat(payload.toString()).doesNotContain("hidden");
    }
}
