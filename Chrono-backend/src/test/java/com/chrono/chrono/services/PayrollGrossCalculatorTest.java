package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.services.payroll.PayrollGrossCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PayrollGrossCalculatorTest {
    private final LocalDate start = LocalDate.of(2026,9,1), end = LocalDate.of(2026,9,30);
    static DailyTimeSummaryDTO day(LocalDate date, int minutes) {
        var day = new DailyTimeSummaryDTO("fixture",date,minutes,0,List.of(),null,false,null);
        day.setHasTrackedEntries(true); day.setExpectedMinutes(0); day.setDifferenceMinutes(minutes); return day;
    }
    @Test void hourlyPayHasNo160HourBoundaryAndNeverPaysBaseHoursTwice() {
        var terms = PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("payType","HOURLY","amount","30","overtimePremiumRate","0"));
        List<DailyTimeSummaryDTO> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d=d.plusDays(1)) if (d.getDayOfWeek().getValue()<6) days.add(day(d,480));
        var result = PayrollGrossCalculator.calculate(terms,start,end,days,null,false,null);
        assertEquals(new BigDecimal("5280.00"),result.total()); // 22 x 8 x 30, no arbitrary 160-hour premium
        assertEquals(0,result.paidOvertimeMinutes());
        assertThrows(IllegalStateException.class, () -> PayrollGrossCalculator.calculate(terms,start,end,days,1.0,true,100000));
    }
    @Test void weeklyPremiumIncludesPriorMonthHoursButPaysOnlyCurrentMonthMinutes() {
        var terms = PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("payType","HOURLY","amount","30","weeklyHours","20"));
        var days = List.of(day(start.minusDays(1),600),day(start,480),day(start.plusDays(1),480));
        var result = PayrollGrossCalculator.calculate(terms,start,end,days,null,false,null);
        assertEquals(new BigDecimal("480.00"),result.base()); // September 16 hours
        assertEquals(new BigDecimal("45.00"),result.premium()); // 6 hours above weekly 20, already paid at base
        assertEquals(new BigDecimal("525.00"),result.total());
    }
    @Test void monthlySalaryDoesNotPayUnrequestedOvertimeAutomatically() {
        var result = PayrollGrossCalculator.calculate(PayrollTestTerms.swiss(),start,end,List.of(day(start,600)),null,false,9000);
        assertEquals(new BigDecimal("5000.00"),result.total()); assertEquals(0,result.paidOvertimeMinutes());
    }
    @Test void explicitPayoutUsesContractRateAndExactMinutes() {
        var result = PayrollGrossCalculator.calculate(PayrollTestTerms.swiss(),start,end,List.of(),1.5,true,90);
        assertEquals(new BigDecimal("56.25"),result.payout()); assertEquals(90,result.paidOvertimeMinutes());
        assertEquals(new BigDecimal("5056.25"),result.total());
        assertThrows(IllegalStateException.class, () -> PayrollGrossCalculator.calculate(PayrollTestTerms.swiss(),start,end,List.of(),1.5,true,89));
        assertThrows(IllegalArgumentException.class, () -> PayrollGrossCalculator.calculate(PayrollTestTerms.swiss(),start,end,List.of(),0.001,true,90));
        assertEquals(1,PayrollGrossCalculator.calculate(PayrollTestTerms.swiss(),start,end,List.of(),1.0/60,true,90).paidOvertimeMinutes());
    }
    @Test void legalExcessTimeCannotBePaidWithAnUnverifiedReducedPremium() {
        var terms = PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("payType","HOURLY","amount","30","overtimePremiumRate","0"));
        var monday = LocalDate.of(2026,9,7);
        var days = java.util.stream.IntStream.range(0,5).mapToObj(i -> day(monday.plusDays(i),600)).toList();
        assertThrows(IllegalStateException.class, () -> PayrollGrossCalculator.calculate(terms,start,end,days,null,false,null));
    }
    @Test void openOrDuplicateDaysCannotProducePay() {
        var invalid = new DailyTimeSummaryDTO("fixture",start,480,0,List.of(),null,false,new DailyTimeSummaryDTO.PrimaryTimes(null,null,true));
        assertThrows(IllegalStateException.class, () -> PayrollGrossCalculator.calculate(PayrollTestTerms.swiss(),start,end,List.of(invalid),null,false,null));
        assertThrows(IllegalStateException.class, () -> PayrollGrossCalculator.calculate(PayrollTestTerms.swiss(),start,end,List.of(day(start,480),day(start,480)),null,false,null));
    }
}
