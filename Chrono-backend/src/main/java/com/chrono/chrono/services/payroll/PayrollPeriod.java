package com.chrono.chrono.services.payroll;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;

public final class PayrollPeriod {
    private PayrollPeriod() {}
    public static boolean fullMonth(LocalDate start, LocalDate end) {
        return start.getDayOfMonth() == 1 && end.equals(start.plusMonths(1).minusDays(1));
    }
    /** ESTV KS45 6.6: February 28/29 and day 31 count as day 30; inclusive, minimum one day. */
    public static int swissDays(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) return 0;
        if (!YearMonth.from(start).equals(YearMonth.from(end))) {
            int days = 0;
            for (LocalDate cursor = start; !cursor.isAfter(end);) {
                LocalDate last = cursor.withDayOfMonth(cursor.lengthOfMonth());
                days += swissDays(cursor, last.isAfter(end) ? end : last);
                cursor = last.plusDays(1);
            }
            return days;
        }
        return Math.max(1, swissDay(end) - swissDay(start) + 1);
    }
    private static int swissDay(LocalDate date) {
        return date.getMonthValue() == 2 && date.getDayOfMonth() >= 28 ? 30 : Math.min(date.getDayOfMonth(), 30);
    }
    public static BigDecimal prorate(BigDecimal monthly, LocalDate start, LocalDate end, String method) {
        if (fullMonth(start, end) || "FULL".equals(method)) return monthly.setScale(2, RoundingMode.HALF_UP);
        if ("THIRTY_DAYS".equals(method)) return monthly.multiply(BigDecimal.valueOf(swissDays(start, end))).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        if ("CALENDAR_DAYS".equals(method)) return monthly.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1)).divide(BigDecimal.valueOf(start.lengthOfMonth()), 2, RoundingMode.HALF_UP);
        throw new IllegalStateException("Für den Teilmonat fehlt die vertragliche Kürzungsmethode.");
    }
}
