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

    @InjectMocks
    private PayrollService payrollService;

    @Test
    void generatePayslip_calculatesGrossNetWithOvertime() {
        User user = new User();
        user.setId(1L);
        user.setUsername("john");
        user.setIsHourly(true);
        user.setHourlyRate(10.0);
        user.setBankAccount("123");
        user.setSocialSecurityNumber("456");

        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        TimeTrackingEntry startEntry = new TimeTrackingEntry();
        startEntry.setPunchType(TimeTrackingEntry.PunchType.START);
        startEntry.setEntryTimestamp(start.atStartOfDay());
        TimeTrackingEntry endEntry = new TimeTrackingEntry();
        endEntry.setPunchType(TimeTrackingEntry.PunchType.ENDE);
        endEntry.setEntryTimestamp(start.atStartOfDay().plusHours(170));
        when(timeTrackingEntryRepository
                .findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(eq(user), any(), any()))
                .thenReturn(List.of(startEntry, endEntry));

        when(payslipRepository.findByUserAndPeriodStartAndPeriodEnd(user, start, end))
                .thenReturn(Collections.emptyList());

        Map<String, Double> emp = Map.of("Tax", 100.0);
        Map<String, Double> empEr = Map.of("Employer", 50.0);
        TaxCalculationService.Result res = new TaxCalculationService.Result(emp, empEr);
        when(taxCalculationService.calculate(eq(user), anyDouble())).thenReturn(res);
        when(payslipRepository.save(any(Payslip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payslip ps = payrollService.generatePayslip(1L, start, end, null);

        assertEquals(1725.0, ps.getGrossSalary());
        assertEquals(100.0, ps.getDeductions());
        assertEquals(1625.0, ps.getNetSalary());
        assertEquals(50.0, ps.getEmployerContributions());
        assertEquals(2, ps.getEarnings().size());
        assertEquals("Base salary", ps.getEarnings().get(0).getType());
        assertEquals("Overtime", ps.getEarnings().get(1).getType());
        verify(emailService).sendPayslipGeneratedMail(user, ps);
        ArgumentCaptor<PayslipAudit> captor = ArgumentCaptor.forClass(PayslipAudit.class);
        verify(payslipAuditRepository).save(captor.capture());
        assertEquals("GENERATED", captor.getValue().getAction());
    }

    @Test
    void generatePayslip_payoutOvertimeAdjustsBalanceAndPay() {
        User user = new User();
        user.setId(1L);
        user.setUsername("john");
        user.setIsHourly(true);
        user.setHourlyRate(10.0);
        user.setTrackingBalanceInMinutes(120);

        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(timeTrackingEntryRepository
                .findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(eq(user), any(), any()))
                .thenReturn(Collections.emptyList());
        when(payslipRepository.findByUserAndPeriodStartAndPeriodEnd(user, start, end))
                .thenReturn(Collections.emptyList());

        Map<String, Double> emp = Map.of();
        Map<String, Double> empEr = Map.of();
        TaxCalculationService.Result res = new TaxCalculationService.Result(emp, empEr);
        when(taxCalculationService.calculate(eq(user), anyDouble())).thenReturn(res);
        when(payslipRepository.save(any(Payslip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payslip ps = payrollService.generatePayslip(1L, start, end, null, 1.5, true);

        assertEquals(18.75, ps.getGrossSalary());
        assertEquals(1.5, ps.getOvertimeHours());
        assertTrue(ps.isPayoutOvertime());
        assertEquals(30, user.getTrackingBalanceInMinutes());
    }

    @Test
    void approvePayslip_marksApprovedAndSendsMail() {
        Payslip ps = new Payslip();
        ps.setId(2L);
        ps.setUser(new User());
        when(payslipRepository.findById(2L)).thenReturn(Optional.of(ps));
        when(pdfService.generatePayslipPdf(ps)).thenReturn("file.pdf");
        when(payslipRepository.save(ps)).thenReturn(ps);

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
    void deletePayslip_throwsWhenApproved() {
        Payslip ps = new Payslip();
        ps.setId(3L);
        ps.setApproved(true);
        when(payslipRepository.findById(3L)).thenReturn(Optional.of(ps));

        assertThrows(IllegalStateException.class, () -> payrollService.deletePayslip(3L));
        verify(payslipRepository, never()).delete(any());
    }
}

