package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsRevenueAutomationDtos.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class PmsDemandForecastTest {
 final LocalDate today=LocalDate.of(2026,9,12);
 List<Observation> observed(){return List.of(new Observation(today.minusWeeks(1),40,new BigDecimal("4000")),new Observation(today.minusWeeks(2),60,new BigDecimal("6000")),new Observation(today.minusWeeks(3),80,new BigDecimal("8000")),new Observation(today.minusWeeks(4),100,new BigDecimal("10000")));}
 @Test void noHistoryIsUnknownRatherThanAZeroForecast(){var value=PmsDemandForecast.predict(today,today,3,new BigDecimal("300"),100,List.of(),List.of(),4,"CHF");assertThat(value.expectedRoomNights()).isNull();assertThat(value.expectedNetRoomRevenue()).isNull();assertThat(value.onBooks()).isEqualTo(3);}
 @Test void comparableWeekdaysHaveAuditableSpanAndNeverEraseConfirmedBookings(){var value=PmsDemandForecast.predict(today,today,90,new BigDecimal("9000"),100,observed(),List.of(),4,"CHF");assertThat(value.expectedRoomNights()).isEqualTo(90);assertThat(value.lowerRoomNights()).isEqualTo(90);assertThat(value.upperRoomNights()).isEqualTo(100);assertThat(value.expectedNetRoomRevenue()).isEqualByComparingTo("9000");assertThat(value.samples()).isEqualTo(4);}
 @Test void observedPickupChangesEstimateAndUnknownTaxesDoNotBecomeRevenue(){var baseline=PmsDemandForecast.predict(today,today,20,BigDecimal.ZERO,100,observed(),List.of(),4,"CHF");var pickup=PmsDemandForecast.predict(today,today,20,null,100,observed(),List.of(10L,10L,10L,10L),4,"CHF");assertThat(baseline.expectedRoomNights()).isEqualTo(70);assertThat(pickup.expectedRoomNights()).isEqualTo(50);assertThat(pickup.method()).contains("PICKUP");assertThat(pickup.expectedNetRoomRevenue()).isNull();}
 @Test void futureObservationsCannotLeakIntoTraining(){List<Observation> history=new ArrayList<>(observed());history.add(new Observation(today.plusWeeks(1),500,new BigDecimal("50000")));assertThat(PmsDemandForecast.predict(today,today,0,BigDecimal.ZERO,100,history,List.of(),4,"CHF").samples()).isEqualTo(4);}
 @Test void boundedPriceUsesBaseRatherThanCompoundingAndRespectsCurrency(){Rule rule=new Rule(1L,new BigDecimal("50"),new BigDecimal("150"),new BigDecimal("5"),40,80,new BigDecimal("20"));assertThat(PmsDemandForecast.price(new BigDecimal("100"),new BigDecimal("100"),rule,90,100,"CHF")).isEqualByComparingTo("105");assertThat(PmsDemandForecast.price(new BigDecimal("100"),new BigDecimal("120"),rule,90,100,"CHF")).isEqualByComparingTo("120");}
 @Test void incompatiblePriceFloorNeverOverridesThePerRunChangeLimit(){Rule rule=new Rule(1L,new BigDecimal("130"),new BigDecimal("150"),new BigDecimal("5"),40,80,new BigDecimal("20"));assertThat(PmsDemandForecast.price(new BigDecimal("140"),new BigDecimal("100"),rule,90,100,"CHF")).isEqualByComparingTo("100");}
}
