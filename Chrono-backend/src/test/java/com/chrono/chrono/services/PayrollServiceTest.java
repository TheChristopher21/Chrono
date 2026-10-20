package com.chrono.chrono.services;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.PayslipAudit;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.PayslipAuditRepository;
import com.chrono.chrono.repositories.PayslipRepository;
import com.chrono.chrono.repositories.PayslipScheduleRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.accounting.AccountingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock
    private PayslipRepository payslipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TimeTrackingEntryRepository timeTrackingEntryRepository;
    @Mock
    private PayslipAuditRepository payslipAuditRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private PdfService pdfService;
    @Mock
    private PayslipScheduleRepository payslipScheduleRepository;
    @Mock
    private TaxCalculationService taxCalculationService;

    @Mock
    private AccountingService accountingService;
    @Mock
    private TimeTrackingService timeTrackingService;

    @Mock
    private PayrollApprovalGuard approvalGuard;

    @Mock
    private PayrollCalculationService calculations;

    @InjectMocks
    private PayrollService payrollService;

    @Test
    void generatePayslipStoresIndependentCalculationAndSnapshotWithoutChangingBalance() {
        User user = new User(); user.setId(1L); user.setUsername("john"); user.setTrackingBalanceInMinutes(120);
        LocalDate start = LocalDate.of(2026,1,1), end = LocalDate.of(2026,1,31);
        var gross = new com.chrono.chrono.services.payroll.PayrollGrossCalculator.Result(
                new java.math.BigDecimal("5000.00"), java.math.BigDecimal.ZERO,
                new java.math.BigDecimal("56.25"), new java.math.BigDecimal("5056.25"),90);
        var calculation = new PayrollCalculationService.Calculation(12L, PayrollTestTerms.swiss(),start,end,List.of(),gross,
                Map.of("Tax",100.0),Map.of("Employer",50.0),"Test reference");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(calculations.calculate(user,start,end,1.5,true)).thenReturn(calculation);
        when(calculations.snapshot(calculation)).thenReturn("saved-inputs");
        when(payslipRepository.save(any(Payslip.class))).thenAnswer(i -> i.getArgument(0));
        Payslip slip = payrollService.generatePayslip(1L,start,end,null,1.5,true);
        assertEquals(5056.25,slip.getGrossSalary()); assertEquals(4956.25,slip.getNetSalary());
        assertEquals(50.0,slip.getEmployerContributions()); assertEquals(1.5,slip.getOvertimeHours());
        assertEquals("CHF",slip.getCurrency()); assertEquals("saved-inputs",slip.getCalculationSnapshot());
        assertEquals(PayrollCalculationService.VERSION,slip.getCalculationVersion());
        assertNull(slip.getVersion()); assertEquals(120,user.getTrackingBalanceInMinutes());
        verify(emailService).sendPayslipGeneratedMail(user,slip);
    }

    @Test
    void rejectedCalculationCreatesNoPayslipOrNotification() {
        User user = new User(); user.setId(38L);
        LocalDate start = LocalDate.of(2026,1,1), end = LocalDate.of(2026,1,31);
        when(userRepository.findById(38L)).thenReturn(Optional.of(user));
        when(calculations.calculate(user,start,end,null,false)).thenThrow(new IllegalStateException("Lohnregeln fehlen"));
        assertThrows(IllegalStateException.class, () -> payrollService.generatePayslip(38L,start,end,null));
        verifyNoInteractions(payslipRepository,emailService,payslipAuditRepository);
    }

    @Test
    void approvePayslip_rebuildsBalanceAfterPersistingApproval() {
        User user = new User();
        user.setTrackingBalanceInMinutes(180);

        Payslip ps = new Payslip();
        ps.setId(42L);
        ps.setUser(user);
        ps.setPayoutOvertime(true);
        ps.setOvertimeHours(2.0);

        when(payslipRepository.findById(42L)).thenReturn(Optional.of(ps));
        when(pdfService.generatePayslipPdf(ps)).thenReturn("file.pdf");
        when(payslipRepository.saveAndFlush(ps)).thenReturn(ps);
        when(accountingService.recordPayrollPosting(any(Payslip.class))).thenReturn(null);

        payrollService.approvePayslip(42L, null);

        assertTrue(ps.isApproved());
        verify(timeTrackingService).rebuildUserBalance(user);
        verify(userRepository, never()).save(user);
    }

    @Test
    void historicalApprovedPayslipCannotBeReopenedOrRedated() {
        Payslip slip = new Payslip(); slip.setId(76L); slip.setApproved(true); slip.setLocked(true);
        slip.setGrossSalary(1234.56); slip.setNetSalary(-10.01); slip.setPdfPath("historical.pdf");
        when(payslipRepository.findById(76L)).thenReturn(Optional.of(slip));
        assertThrows(IllegalStateException.class, () -> payrollService.reopenPayslip(76L));
        assertThrows(IllegalStateException.class, () -> payrollService.setPayoutDate(76L,LocalDate.of(2026,1,1)));
        assertTrue(slip.isApproved()); assertTrue(slip.isLocked());
        assertEquals(-10.01,slip.getNetSalary()); assertEquals("historical.pdf",slip.getPdfPath());
        verifyNoInteractions(timeTrackingService,accountingService,pdfService,emailService,payslipAuditRepository);
        verify(payslipRepository,never()).saveAndFlush(any()); verify(payslipRepository,never()).save(any());
    }

    @Test
    void approvePayslip_marksApprovedAndSendsMail() {
        Payslip ps = new Payslip();
        ps.setId(2L);
        ps.setUser(new User());
        when(payslipRepository.findById(2L)).thenReturn(Optional.of(ps));
        when(pdfService.generatePayslipPdf(ps)).thenReturn("file.pdf");
        when(payslipRepository.saveAndFlush(ps)).thenReturn(ps);
        when(accountingService.recordPayrollPosting(any(Payslip.class))).thenReturn(null);

        payrollService.approvePayslip(2L, "ok");

        assertTrue(ps.isApproved());
        assertTrue(ps.isLocked());
        assertEquals("file.pdf", ps.getPdfPath());
        verify(emailService).sendPayslipApprovedMail(ps.getUser(), ps);
        ArgumentCaptor<PayslipAudit> captor = ArgumentCaptor.forClass(PayslipAudit.class);
        verify(payslipAuditRepository).save(captor.capture());
        assertEquals("APPROVED", captor.getValue().getAction());
        assertEquals("ok", captor.getValue().getComment());
    }

    @Test
    void approvePayslip_isIdempotentWhenAlreadyApproved() {
        User user = new User();
        Payslip ps = new Payslip();
        ps.setId(2L);
        ps.setUser(user);
        ps.setApproved(true);
        when(payslipRepository.findById(2L)).thenReturn(Optional.of(ps));

        payrollService.approvePayslip(2L, "again");

        verify(timeTrackingService).rebuildUserBalance(user);
        verify(payslipRepository, never()).saveAndFlush(any());
        verify(accountingService, never()).recordPayrollPosting(any());
        verify(payslipAuditRepository, never()).save(any());
        verify(emailService, never()).sendPayslipApprovedMail(any(), any());
    }

    @Test
    void approveAllForUser_skipsAlreadyApprovedPayslips() {
        User user = new User();
        user.setId(5L);

        Payslip approved = new Payslip();
        approved.setId(10L);
        approved.setUser(user);
        approved.setApproved(true);

        Payslip pending = new Payslip();
        pending.setId(11L);
        pending.setUser(user);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(payslipRepository.findByUser(user)).thenReturn(List.of(approved, pending));
        when(pdfService.generatePayslipPdf(pending)).thenReturn("file.pdf");

        payrollService.approveAllForUser(5L, "batch");

        verify(payslipRepository).saveAllAndFlush(List.of(pending));
        verify(accountingService).recordPayrollPosting(pending);
        verify(accountingService, never()).recordPayrollPosting(approved);
        verify(emailService).sendPayslipApprovedMail(user, pending);
        verify(emailService, never()).sendPayslipApprovedMail(user, approved);
        verify(timeTrackingService).rebuildUserBalance(user);
    }

    @Test
    void deletePayslip_throwsWhenApproved() {
        Payslip ps = new Payslip();
        ps.setId(3L);
        ps.setApproved(true);
        when(payslipRepository.findById(3L)).thenReturn(Optional.of(ps));

        assertThrows(IllegalStateException.class, () -> payrollService.deletePayslip(3L));
        verify(payslipRepository, never()).delete(any());
    }

    @Test
    void batchChecksEverySelectedPayslipBeforeAnyPdfPostingOrEmail() {
        Payslip first = new Payslip(); first.setId(1L);
        Payslip second = new Payslip(); second.setId(2L);
        when(payslipRepository.findAllById(List.of(1L,2L))).thenReturn(List.of(first,second));
        doThrow(new IllegalStateException("Invalid second payslip")).when(approvalGuard).validate(List.of(first,second));
        assertThrows(IllegalStateException.class, () -> payrollService.approveBatch(List.of(2L,1L,1L), "batch"));
        verifyNoInteractions(pdfService,accountingService,emailService);
        verify(payslipRepository,never()).saveAndFlush(any());
    }

    @Test
    void approvalNotificationWaitsUntilCommitAndIsNotSentOnRollback() {
        Payslip ps = new Payslip(); ps.setId(2L); ps.setUser(new User());
        when(payslipRepository.findById(2L)).thenReturn(Optional.of(ps));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            payrollService.approvePayslip(2L, "test");
            verifyNoInteractions(emailService);
            var callbacks = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            callbacks.forEach(callback -> callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(emailService);
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
        ps.setApproved(false);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            payrollService.approvePayslip(2L, "test");
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(emailService).sendPayslipApprovedMail(ps.getUser(),ps);
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test
    void committedApprovalDoesNotReportFailureWhenNotificationDeliveryFails() {
        Payslip ps = new Payslip(); ps.setId(2L); ps.setUser(new User());
        when(payslipRepository.findById(2L)).thenReturn(Optional.of(ps));
        doThrow(new IllegalStateException("Mail unavailable")).when(emailService).sendPayslipApprovedMail(ps.getUser(), ps);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            payrollService.approvePayslip(2L, "test");
            assertDoesNotThrow(() -> org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit));
            assertTrue(ps.isApproved());
            verify(payslipRepository, times(1)).saveAndFlush(ps);
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }
}
