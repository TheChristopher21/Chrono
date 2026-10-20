package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.RatePlan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Shared pricing rules for availability, reservations and the generated folio. */
final class PmsRatePricing {
    private PmsRatePricing() { }

    record NightPrice(BigDecimal accommodation, BigDecimal breakfast) {
        BigDecimal total() { return accommodation.add(breakfast); }
    }

    static NightPrice night(RatePlan rate, BigDecimal enteredNightly, int adults, int children) {
        PmsMoney.require(enteredNightly, rate.getCurrencyCode());
        PmsMoney.require(zero(rate.getBreakfastAmount()), rate.getCurrencyCode());
        PmsMoney.require(zero(rate.getExtraAdultRate()), rate.getCurrencyCode());
        PmsMoney.require(zero(rate.getChildRate()), rate.getCurrencyCode());
        BigDecimal breakfast = rate.isBreakfastIncluded() ? zero(rate.getBreakfastAmount()) : BigDecimal.ZERO;
        if (breakfast.compareTo(enteredNightly) > 0) {
            throw new IllegalArgumentException("Der Frühstücksanteil übersteigt den Tagespreis.");
        }
        BigDecimal supplements = zero(rate.getExtraAdultRate())
                .multiply(BigDecimal.valueOf(Math.max(0, adults - rate.getIncludedAdults())))
                .add(zero(rate.getChildRate()).multiply(BigDecimal.valueOf(children)));
        return new NightPrice(gross(enteredNightly.subtract(breakfast).add(supplements), rate.getVatRate(), rate.isTaxIncluded(), rate.getCurrencyCode()),
                gross(breakfast, rate.getBreakfastVatRate(), rate.isTaxIncluded(), rate.getCurrencyCode()));
    }

    static String restriction(RatePlan rate, LocalDate arrival, LocalDate departure, LocalDate bookingDate) {
        long nights = ChronoUnit.DAYS.between(arrival, departure);
        if (rate.getValidFrom() != null && arrival.isBefore(rate.getValidFrom())
                || rate.getValidTo() != null && departure.minusDays(1).isAfter(rate.getValidTo())) {
            return "Der Aufenthalt liegt außerhalb der Ratengültigkeit.";
        }
        if (rate.getBookingFrom() != null && bookingDate.isBefore(rate.getBookingFrom())
                || rate.getBookingTo() != null && bookingDate.isAfter(rate.getBookingTo())) {
            return "Der Ratenplan ist außerhalb des Verkaufszeitraums.";
        }
        if (rate.getMaxStay() != null && nights > rate.getMaxStay()) {
            return "Der Höchstaufenthalt beträgt " + rate.getMaxStay() + " Nächte.";
        }
        long leadDays = ChronoUnit.DAYS.between(bookingDate, arrival);
        if (rate.getMinAdvanceDays() != null && leadDays < rate.getMinAdvanceDays()) {
            return "Die Rate erfordert mindestens " + rate.getMinAdvanceDays() + " Tage Vorausbuchung.";
        }
        if (rate.getMaxAdvanceDays() != null && leadDays > rate.getMaxAdvanceDays()) {
            return "Die Rate ist höchstens " + rate.getMaxAdvanceDays() + " Tage im Voraus buchbar.";
        }
        return null;
    }

    static BigDecimal gross(BigDecimal amount, BigDecimal taxRate, boolean included, String currencyCode) {
        return PmsMoney.round(included || taxRate == null ? amount
                : amount.multiply(BigDecimal.ONE.add(taxRate.movePointLeft(2))), currencyCode);
    }

    private static BigDecimal zero(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }
}
