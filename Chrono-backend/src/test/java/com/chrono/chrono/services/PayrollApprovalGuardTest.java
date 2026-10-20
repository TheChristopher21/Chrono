package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollApprovalGuardTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PayslipRepository slips = mock(PayslipRepository.class);
    private final EntityManager entities = mock(EntityManager.class);
    private final TimeTrackingService tracking = mock(TimeTrackingService.class);
    private final PayrollApprovalGuard guard = new PayrollApprovalGuard(users, slips, entities, mock(PayrollCalculationService.class), tracking);

    private Payslip draft(long id) {
        User user = new User(); user.setId(7L); user.setTrackingBalanceInMinutes(120);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        Payslip slip = new Payslip(); slip.setId(id); slip.setUser(user);
        slip.setPeriodStart(LocalDate.of(2026, 9, 1)); slip.setPeriodEnd(LocalDate.of(2026, 9, 30));
        slip.setGrossSalary(5000.0); slip.setDeductions(650.0); slip.setNetSalary(4350.0); slip.setEmployerContributions(700.0);
        slip.setEarnings(List.of(new PayComponent("Lohn", 5000.0)));
        slip.setDeductionsList(List.of(new PayComponent("AHV", 265.0), new PayComponent("Weitere", 385.0)));
        slip.setEmployerContribList(List.of(new PayComponent("Beiträge", 700.0)));
        return slip;
    }

    @Test void acceptsBalancedAmountsAndDoesNotChangeThePayslip() {
        Payslip slip = draft(1);
        guard.validate(List.of(slip));
        assertFalse(slip.isApproved());
        assertEquals(4350.0, slip.getNetSalary());
        verify(entities).refresh(slip, LockModeType.PESSIMISTIC_WRITE);
        verify(slips, never()).save(any());
    }
    @Test void rejectsNegativeNetBeforeAnyWrite() {
        Payslip slip = draft(1); slip.setNetSalary(-1.0);
        assertThrows(IllegalStateException.class, () -> guard.validate(List.of(slip)));
        verify(slips, never()).save(any());
    }
    @Test void rejectsNonFiniteAndMissingMoney() {
        Payslip slip = draft(1); slip.setGrossSalary(Double.NaN);
        assertThrows(IllegalStateException.class, () -> PayrollApprovalGuard.validateAmounts(slip));
        slip.setGrossSalary(Double.POSITIVE_INFINITY);
        assertThrows(IllegalStateException.class, () -> PayrollApprovalGuard.validateAmounts(slip));
        slip.setGrossSalary(null);
        assertThrows(IllegalStateException.class, () -> PayrollApprovalGuard.validateAmounts(slip));
    }
    @Test void checksNetAndComponentsIndependently() {
        Payslip slip = draft(1); slip.setNetSalary(4349.99);
        assertThrows(IllegalStateException.class, () -> PayrollApprovalGuard.validateAmounts(slip));
        slip.setNetSalary(4350.0); slip.setDeductionsList(List.of(new PayComponent("Abzug", 649.99)));
        assertThrows(IllegalStateException.class, () -> PayrollApprovalGuard.validateAmounts(slip));
    }
    @Test void acceptsBinaryFloatingPointResidueAtTheCentBoundary() {
        Payslip slip = draft(1); slip.setGrossSalary(0.1 + 0.2); slip.setDeductions(0.1); slip.setNetSalary(0.2);
        slip.setEarnings(List.of(new PayComponent("A", 0.1), new PayComponent("B", 0.2)));
        slip.setDeductionsList(List.of(new PayComponent("Abzug", 0.1)));
        assertDoesNotThrow(() -> PayrollApprovalGuard.validateAmounts(slip));
    }
    @Test void rejectsOverlapWithAnApprovedPeriodIncludingSharedBoundary() {
        Payslip existing = draft(1); existing.setApproved(true); existing.setPeriodEnd(LocalDate.of(2026, 9, 1));
        Payslip next = draft(2);
        when(slips.findApprovedForUpdate(next.getUser())).thenReturn(List.of(existing));
        assertThrows(IllegalStateException.class, () -> guard.validate(List.of(next)));
    }
    @Test void rejectsOverlappingDraftsInOneBatch() {
        Payslip a = draft(1); Payslip b = draft(2);
        assertThrows(IllegalStateException.class, () -> guard.validate(List.of(a, b)));
        assertFalse(a.isApproved()); assertFalse(b.isApproved());
    }
    @Test void rejectsAccumulatedOvertimeBeyondBalance() {
        Payslip a = draft(1); Payslip b = draft(2);
        a.setPeriodStart(LocalDate.of(2026, 8, 1)); a.setPeriodEnd(LocalDate.of(2026, 8, 31));
        for (Payslip slip : List.of(a,b)) { slip.setPayoutOvertime(true); slip.setOvertimeHours(1.5); }
        when(tracking.getPayrollOvertimeBalance(any(), any())).thenReturn(120);
        assertThrows(IllegalStateException.class, () -> guard.validate(List.of(a,b)));
    }
    @Test void acceptsAnExactMinuteAndRejectsFractionalMinutes() {
        Payslip a = draft(1); a.setPayoutOvertime(true); a.setOvertimeHours(1.0 / 60);
        when(tracking.getPayrollOvertimeBalance(any(), any())).thenReturn(1);
        assertDoesNotThrow(() -> guard.validate(List.of(a)));
        a.setOvertimeHours(0.001);
        assertThrows(IllegalArgumentException.class, () -> guard.validate(List.of(a)));
    }
    @Test void cumulativeBatchCannotSpendLaterEarnedHoursOnEarlierPeriods() {
        Payslip a = draft(1), b = draft(2);
        a.setPeriodStart(LocalDate.of(2026, 8, 1)); a.setPeriodEnd(LocalDate.of(2026, 8, 31));
        for (Payslip slip : List.of(a,b)) { slip.setPayoutOvertime(true); slip.setOvertimeHours(1.0); }
        when(tracking.getPayrollOvertimeBalance(any(), eq(a.getPeriodEnd()))).thenReturn(60);
        when(tracking.getPayrollOvertimeBalance(any(), eq(b.getPeriodEnd()))).thenReturn(90);
        var error = assertThrows(IllegalStateException.class, () -> guard.validate(List.of(b,a)));
        assertTrue(error.getMessage().contains("gesammelten Auszahlungen"));
        when(tracking.getPayrollOvertimeBalance(any(), eq(b.getPeriodEnd()))).thenReturn(120);
        assertDoesNotThrow(() -> guard.validate(List.of(b,a)));
    }
    @Test void leavesAlreadyApprovedHistoricalValuesUntouched() {
        Payslip old = draft(1); old.setApproved(true); old.setNetSalary(-10.0);
        assertDoesNotThrow(() -> guard.validate(List.of(old)));
        assertEquals(-10.0, old.getNetSalary());
    }
}
