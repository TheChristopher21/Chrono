package com.chrono.chrono.services;

import com.chrono.chrono.dto.InvoiceLineDTO;
import com.chrono.chrono.dto.InvoiceSummaryDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Task;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private TimeTrackingEntryRepository timeTrackingEntryRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @InjectMocks
    private BillingService billingService;

    @Test
    void generateInvoice_groupsBillableEntriesAndRecordsAudit() {
        Company company = new Company();
        company.setId(1L);

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setName("ACME");
        customer.setCompany(company);

        Project project = new Project();
        project.setId(10L);
        project.setName("Root Project");
        project.setCustomer(customer);
        project.setHourlyRate(new BigDecimal("120.00"));

        Project childProject = new Project();
        childProject.setId(11L);
        childProject.setName("Child Project");
        childProject.setCustomer(customer);

        Task billableTask = new Task();
        billableTask.setId(100L);
        billableTask.setName("Implementation");
        billableTask.setBillable(true);

        Task nonBillableTask = new Task();
        nonBillableTask.setId(101L);
        nonBillableTask.setName("Internal");
        nonBillableTask.setBillable(false);

        TimeTrackingEntry billableEntry = new TimeTrackingEntry();
        billableEntry.setProject(project);
        billableEntry.setTask(billableTask);
        billableEntry.setDurationMinutes(120);
        billableEntry.setEntryTimestamp(LocalDateTime.of(2024, 1, 5, 10, 0));

        TimeTrackingEntry childEntry = new TimeTrackingEntry();
        childEntry.setProject(childProject);
        childEntry.setTask(null);
        childEntry.setDurationMinutes(30);
        childEntry.setEntryTimestamp(LocalDateTime.of(2024, 1, 6, 9, 0));

        TimeTrackingEntry nonBillableEntry = new TimeTrackingEntry();
        nonBillableEntry.setProject(project);
        nonBillableEntry.setTask(nonBillableTask);
        nonBillableEntry.setDurationMinutes(45);
        nonBillableEntry.setEntryTimestamp(LocalDateTime.of(2024, 1, 7, 9, 0));

        when(projectService.collectProjectAndDescendantIds(project)).thenReturn(Set.of(10L, 11L));
        when(timeTrackingEntryRepository.findByProjectIdInAndEntryTimestampBetween(anyList(), any(), any()))
                .thenReturn(List.of(billableEntry, childEntry, nonBillableEntry));

        User actor = new User();
        actor.setUsername("auditor");
        actor.setCompany(company);

        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);

        InvoiceSummaryDTO summary = billingService.generateInvoice(
                actor,
                project,
                start,
                end,
                true,
                new BigDecimal("150"),
                "EUR"
        );

        assertEquals(project.getId(), summary.getProjectId());
        assertEquals("Root Project", summary.getProjectName());
        assertEquals("ACME", summary.getCustomerName());
        assertEquals(150, summary.getTotalBillableMinutes());
        assertEquals(new BigDecimal("375.00"), summary.getTotalAmount());
        assertEquals("EUR", summary.getCurrency());

        assertEquals(2, summary.getLineItems().size());
        InvoiceLineDTO taskLine = summary.getLineItems().stream()
                .filter(line -> billableTask.getId().equals(line.getTaskId()))
                .findFirst()
                .orElseThrow();
        assertEquals(120, taskLine.getMinutes());
        assertEquals(new BigDecimal("300.00"), taskLine.getAmount());

        InvoiceLineDTO generalLine = summary.getLineItems().stream()
                .filter(line -> line.getTaskId() == null && childProject.getId().equals(line.getProjectId()))
                .findFirst()
                .orElseThrow();
        assertEquals(30, generalLine.getMinutes());
        assertEquals(new BigDecimal("75.00"), generalLine.getAmount());
        assertEquals("Allgemeine Projektarbeit", generalLine.getTaskName());
        assertNotNull(summary.getOverrideRate());

        verify(projectService).collectProjectAndDescendantIds(project);
        verify(timeTrackingEntryRepository).findByProjectIdInAndEntryTimestampBetween(anyList(), any(), any());
        verify(complianceAuditService).recordAction(eq(actor), eq("GENERATE"), eq("BILLING"), eq(project.getId()),
                contains("Root Project"));
    }
}
