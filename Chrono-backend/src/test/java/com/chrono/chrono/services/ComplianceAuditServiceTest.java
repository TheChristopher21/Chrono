package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.ComplianceAuditLog;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.ComplianceAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceAuditServiceTest {

    @Mock
    private ComplianceAuditLogRepository complianceAuditLogRepository;

    @InjectMocks
    private ComplianceAuditService complianceAuditService;

    @Test
    void recordAction_persistsLogWithDefaultSeverity() {
        Company company = new Company();
        company.setId(3L);

        User actor = new User();
        actor.setUsername("anna");
        actor.setCompany(company);

        complianceAuditService.recordAction(actor, "UPDATE", "PROJECT", 99L, "Changed budget");

        ArgumentCaptor<ComplianceAuditLog> captor = ArgumentCaptor.forClass(ComplianceAuditLog.class);
        verify(complianceAuditLogRepository).save(captor.capture());

        ComplianceAuditLog log = captor.getValue();
        assertSame(company, log.getCompany());
        assertEquals("anna", log.getUsername());
        assertEquals("UPDATE", log.getAction());
        assertEquals("PROJECT", log.getTargetType());
        assertEquals(99L, log.getTargetId());
        assertEquals("Changed budget", log.getDetails());
        assertEquals(ComplianceAuditLog.Severity.INFO, log.getSeverity());
    }

    @Test
    void recordAction_ignoresActorWithoutCompany() {
        User actor = new User();
        actor.setUsername("bob");

        complianceAuditService.recordAction(actor, "DELETE", "PROJECT", 1L, "Removed");

        verify(complianceAuditLogRepository, never()).save(any());
    }

    @Test
    void getRecentLogs_clampsLimitAndDelegatesToRepository() {
        ComplianceAuditLog log = new ComplianceAuditLog();
        when(complianceAuditLogRepository.findByCompanyIdOrderByCreatedAtDesc(eq(7L), any(PageRequest.class)))
                .thenReturn(List.of(log));

        List<ComplianceAuditLog> result = complianceAuditService.getRecentLogs(7L, 500);

        assertEquals(List.of(log), result);

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(complianceAuditLogRepository).findByCompanyIdOrderByCreatedAtDesc(eq(7L), pageCaptor.capture());
        PageRequest pageRequest = pageCaptor.getValue();
        assertEquals(0, pageRequest.getPageNumber());
        assertEquals(200, pageRequest.getPageSize());
    }
}
