package com.chrono.chrono.services;

import com.chrono.chrono.dto.TimePeriodSummaryDTO;
import com.chrono.chrono.entities.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollCalculationServiceTest {
    final PayrollTermsService terms=mock(PayrollTermsService.class);
    final TimeTrackingService tracking=mock(TimeTrackingService.class);
    final TaxCalculationService tax=mock(TaxCalculationService.class);
    final EmploymentModelHistoryService history=mock(EmploymentModelHistoryService.class);
    final PayrollLedgerService ledger=mock(PayrollLedgerService.class);
    final PayrollCalculationService service=new PayrollCalculationService(terms,tracking,tax,new ObjectMapper().findAndRegisterModules(),history,ledger);
    final LocalDate start=LocalDate.of(2026,1,1),end=LocalDate.of(2026,1,31);
    final User user=new User();
    private Payslip draft() {
        var rule=PayrollTestTerms.swiss();
        when(terms.resolve(user,start,end)).thenReturn(new PayrollTermsService.Resolved(1L,rule));
        when(tracking.getUserPeriodSummary(user,start,end)).thenReturn(new TimePeriodSummaryDTO("fixture",start,end,480,0,480,0,0,List.of(PayrollGrossCalculatorTest.day(start,480))));
        when(ledger.before(eq(user),any(),any(),anyMap())).thenReturn(new PayrollLedgerService.History(List.of(),List.of(),true));
        when(ledger.thirteenth(any(),any(),any(),any(),any())).thenReturn(java.math.BigDecimal.ZERO);
        when(ledger.read(any())).thenAnswer(i -> new ObjectMapper().findAndRegisterModules().readValue(((Payslip)i.getArgument(0)).getCalculationSnapshot(), PayrollCalculationService.Calculation.class));
        when(tax.calculateRun(eq(user),any(),eq(start),eq(end),eq(rule),any())).thenReturn(new TaxCalculationService.Result(Map.of("AHV",265.0),Map.of("AHV",265.0),"rules 2026"));
        var calculation=service.calculate(user,start,end,null,false);
        Payslip slip=new Payslip(); slip.setUser(user); slip.setPeriodStart(start); slip.setPeriodEnd(end);
        slip.setGrossSalary(5000.0); slip.setDeductions(265.0); slip.setNetSalary(4735.0); slip.setEmployerContributions(265.0); slip.setCurrency("CHF");
        slip.setEarnings(List.of(new PayComponent("Base salary",5000.0))); slip.setDeductionsList(List.of(new PayComponent("AHV",265.0))); slip.setEmployerContribList(List.of(new PayComponent("AHV",265.0)));
        slip.setCalculationVersion(PayrollCalculationService.VERSION); slip.setCalculationSnapshot(service.snapshot(calculation)); return slip;
    }
    @Test void validatesUnchangedSnapshotButRejectsEditedComponentsEvenIfTotalMatches() {
        var slip=draft(); assertDoesNotThrow(()->service.validateDraft(slip));
        slip.setEarnings(List.of(new PayComponent("Base salary",4900.0),new PayComponent("Invented premium",100.0)));
        assertThrows(IllegalStateException.class,()->service.validateDraft(slip));
    }
    @Test void changedTimeOrNewEffectiveRulesRequireNewDraftWithoutMutatingOldAmounts() {
        var slip=draft(); String original=slip.getCalculationSnapshot();
        when(tracking.getUserPeriodSummary(user,start,end)).thenReturn(new TimePeriodSummaryDTO("fixture",start,end,600,0,480,120,0,List.of(PayrollGrossCalculatorTest.day(start,600))));
        assertThrows(IllegalStateException.class,()->service.validateDraft(slip));
        assertEquals(original,slip.getCalculationSnapshot()); assertEquals(5000.0,slip.getGrossSalary());
    }
    @Test void refusesLegacyDraftInsteadOfRecalculatingItInPlace() {
        var slip=new Payslip(); slip.setGrossSalary(5000.01);
        assertThrows(IllegalStateException.class,()->service.validateDraft(slip));
        assertEquals(5000.01,slip.getGrossSalary()); verifyNoInteractions(terms,tracking,tax);
    }
    @Test void overtimeCannotUseHoursEarnedAfterPeriodEnd() {
        var slip=draft(); user.setTrackingBalanceInMinutes(600);
        when(tracking.getPayrollOvertimeBalance(user,end)).thenReturn(60);
        assertThrows(IllegalStateException.class,()->service.calculate(user,start,end,2.0,true));
    }
    @Test void hourlyPayCannotApplyNewMonthlyRulesToThePreviousPartOfTheSameWeek() {
        var rule=PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("payType","HOURLY","amount","30"));
        when(terms.resolve(user,start,end)).thenReturn(new PayrollTermsService.Resolved(2L,rule));
        when(terms.resolve(user,LocalDate.of(2025,12,29),end)).thenThrow(new IllegalStateException("Lohnregeln ändern innerhalb des Zeitraums"));
        assertThrows(IllegalStateException.class,()->service.calculate(user,start,end,null,false));
        verifyNoInteractions(tracking,tax);
    }
    @Test void payoutRejectsAContractWhoseWeeklyHoursDisagreeWithTheTimeAccount() {
        draft(); user.setTrackingBalanceInMinutes(600); user.setIsHourly(false);
        user.setDailyWorkHours(8.5); user.setExpectedWorkDays(5);
        when(history.resolveUserSnapshotsForRange(user,start,end)).thenReturn(Map.of(start,user));
        var failure=assertThrows(IllegalStateException.class,()->service.calculate(user,start,end,1.0,true));
        assertTrue(failure.getMessage().contains("Arbeitszeitmodell"));
        user.setDailyWorkHours(8.0);
        when(tracking.getPayrollOvertimeBalance(user,end)).thenReturn(600);
        assertEquals(60, service.calculate(user,start,end,1.0,true).gross().paidOvertimeMinutes());
    }
}
