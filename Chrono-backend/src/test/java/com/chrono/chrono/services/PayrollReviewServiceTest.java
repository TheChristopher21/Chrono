package com.chrono.chrono.services;

import com.chrono.chrono.dto.*;
import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.PayslipRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollReviewServiceTest {
    private final TimeTrackingService tracking = mock(TimeTrackingService.class);
    private final PayslipRepository slips = mock(PayslipRepository.class);
    private final PayrollConfigurationService configuration = mock(PayrollConfigurationService.class);
    private final PayrollCalculationService calculations = mock(PayrollCalculationService.class);
    private final PayrollReviewService service = new PayrollReviewService(tracking, slips, configuration, calculations);
    private final LocalDate start = LocalDate.of(2025, 9, 1), end = LocalDate.of(2025, 9, 30);

    @Test void surfacesMissingInsuranceWithoutInventingDefaultsOrWritingData() {
        User user = new User(); user.setId(1L); user.setCountry("CH"); user.setMonthlySalary(5000.0);
        user.setBirthDate(LocalDate.of(1990,1,1)); user.setCompany(new Company());
        when(calculations.calculate(user,start,end,null,false)).thenThrow(new IllegalStateException("Versicherungswerte fehlen"));
        when(tracking.getUserPeriodSummary(user,start,end)).thenReturn(new TimePeriodSummaryDTO("test",start,end,9600,600,9500,100,900,List.of()));
        var result = service.review(user,start,end);
        assertFalse(result.ready()); assertFalse(result.taxCalculationVerified());
        assertEquals(9600,result.workedMinutes()); assertEquals(100,result.differenceMinutes());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("CALCULATION_BLOCKED")));
        verify(tracking,never()).rebuildUserBalance(any()); verify(slips,never()).save(any());
    }
    @Test void detectsMissingTimeAndApprovedOverlap() {
        User user = new User(); user.setId(1L); user.setCountry("DE"); user.setHourlyRate(20.0); user.setIsHourly(true);
        var day = new DailyTimeSummaryDTO("test",start,0,0,List.of(),null,false,null); day.setExpectedMinutes(480);
        when(tracking.getUserPeriodSummary(user,start,end)).thenReturn(new TimePeriodSummaryDTO("test",start,end,0,0,480,-480,0,List.of(day)));
        Payslip existing = new Payslip(); existing.setId(2L); existing.setPeriodStart(start); existing.setPeriodEnd(end);
        when(slips.findByUserAndApproved(user,true)).thenReturn(List.of(existing));
        var codes = service.review(user,start,end).issues().stream().map(PayrollReviewDTO.Issue::code).toList();
        assertTrue(codes.containsAll(List.of("MISSING_TIME","APPROVED_OVERLAP")));
    }
    @Test void rejectsInvalidPeriodsBeforeLoadingAnyData() {
        assertThrows(IllegalArgumentException.class, () -> service.review(new User(),end,start));
        assertThrows(IllegalArgumentException.class, () -> service.review(new User(),null,end));
        assertThrows(IllegalArgumentException.class, () -> service.review(new User(),start,start.plusDays(366)));
        verifyNoInteractions(tracking,slips);
    }
}
