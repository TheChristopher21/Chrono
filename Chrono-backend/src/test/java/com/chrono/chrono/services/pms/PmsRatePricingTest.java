package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.RatePlan;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class PmsRatePricingTest {
    private RatePlan rate() {
        RatePlan rate = new RatePlan();
        rate.setCurrencyCode("CHF");
        rate.setNightlyRate(new BigDecimal("120.00"));
        rate.setVatRate(new BigDecimal("7"));
        rate.setBreakfastIncluded(true);
        rate.setBreakfastAmount(new BigDecimal("20.00"));
        rate.setBreakfastVatRate(new BigDecimal("19"));
        return rate;
    }

    @Test void inclusivePricesKeepGuestTotalWithoutAddingTaxAgain() {
        PmsRatePricing.NightPrice price = PmsRatePricing.night(rate(), new BigDecimal("120"), 1, 0);
        assertThat(price.accommodation()).isEqualByComparingTo("100.00");
        assertThat(price.breakfast()).isEqualByComparingTo("20.00");
        assertThat(price.total()).isEqualByComparingTo("120.00");
    }

    @Test void netPriceSeparatesTaxBasesAndIncludesActualOccupancySupplements() {
        RatePlan rate = rate();
        rate.setTaxIncluded(false);
        rate.setExtraAdultRate(new BigDecimal("10"));
        rate.setChildRate(new BigDecimal("5"));
        PmsRatePricing.NightPrice price = PmsRatePricing.night(rate, new BigDecimal("120"), 2, 2);
        assertThat(price.accommodation()).isEqualByComparingTo("128.40");
        assertThat(price.breakfast()).isEqualByComparingTo("23.80");
        assertThat(price.total()).isEqualByComparingTo("152.20");
    }

    @Test void lowerDailyOverrideCannotMakeTheAccommodationAllocationNegative() {
        assertThatThrownBy(() -> PmsRatePricing.night(rate(), new BigDecimal("10"), 1, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Frühstücksanteil");
    }

    @Test void finalValidNightAllowsDepartureTheFollowingDay() {
        RatePlan rate = rate();
        LocalDate start = LocalDate.of(2030, 1, 10);
        rate.setValidFrom(start); rate.setValidTo(start.plusDays(2));
        assertThat(PmsRatePricing.restriction(rate, start, start.plusDays(3), start)).isNull();
        assertThat(PmsRatePricing.restriction(rate, start, start.plusDays(4), start)).contains("Ratengültigkeit");
        assertThat(PmsRatePricing.restriction(rate, start.minusDays(1), start, start)).contains("Ratengültigkeit");
    }

    @Test void bookingWindowMaxStayAndLeadTimeAreEnforcedAtTheirBoundaries() {
        RatePlan rate = rate(); LocalDate booking = LocalDate.of(2030, 1, 1);
        rate.setBookingFrom(booking); rate.setBookingTo(booking);
        rate.setMinAdvanceDays(2); rate.setMaxAdvanceDays(20); rate.setMaxStay(3);
        assertThat(PmsRatePricing.restriction(rate, booking.plusDays(2), booking.plusDays(5), booking)).isNull();
        assertThat(PmsRatePricing.restriction(rate, booking.plusDays(2), booking.plusDays(6), booking)).contains("Höchstaufenthalt");
        assertThat(PmsRatePricing.restriction(rate, booking.plusDays(1), booking.plusDays(2), booking)).contains("mindestens 2");
        assertThat(PmsRatePricing.restriction(rate, booking.plusDays(21), booking.plusDays(22), booking)).contains("höchstens 20");
        assertThat(PmsRatePricing.restriction(rate, booking.plusDays(2), booking.plusDays(3), booking.minusDays(1))).contains("Verkaufszeitraum");
    }
}
