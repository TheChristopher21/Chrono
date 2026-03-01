package com.chrono.chrono.services;

import com.chrono.chrono.dto.InvoiceLineDTO;
import com.chrono.chrono.dto.InvoiceSummaryDTO;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Task;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chrono.chrono.services.accounting.AccountsReceivableService;

@Service
public class BillingService {

    @Autowired
    private TimeTrackingEntryRepository timeTrackingEntryRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ComplianceAuditService complianceAuditService;

    @Autowired
    private AccountsReceivableService accountsReceivableService;

    public InvoiceSummaryDTO generateInvoice(User actor, Project project, LocalDate start, LocalDate end,
                                             boolean includeChildren, BigDecimal overrideRate, String currency) {
        if (project == null || project.getCustomer() == null || project.getCustomer().getCompany() == null) {
            throw new IllegalArgumentException("Project with valid company required");
        }
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("Invalid billing period");
        }
        Set<Long> projectIds = includeChildren
                ? projectService.collectProjectAndDescendantIds(project)
                : Set.of(project.getId());

        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.plusDays(1).atStartOfDay();
        List<Long> idList = new ArrayList<>(projectIds);

        List<TimeTrackingEntry> entries = idList.isEmpty()
                ? List.of()
                : timeTrackingEntryRepository.findByProjectIdInAndEntryTimestampBetween(idList, startDt, endDt);

        BigDecimal rateToUse = overrideRate != null ? overrideRate : project.getHourlyRate();
        if (rateToUse == null) {
            rateToUse = BigDecimal.ZERO;
        }

        Map<String, InvoiceLineDTO> lines = new LinkedHashMap<>();
        for (TimeTrackingEntry entry : entries) {
            if (entry.getDurationMinutes() == null || entry.getDurationMinutes() <= 0) {
                continue;
            }
            Task task = entry.getTask();
            boolean billable = task == null || task.getBillable() == null || Boolean.TRUE.equals(task.getBillable());
            if (!billable) {
                continue;
            }
            Project entryProject = entry.getProject() != null ? entry.getProject() : project;
            Long projectId = entryProject.getId();
            String taskKey = projectId + ":" + (task != null ? task.getId() : "_none");
            InvoiceLineDTO line = lines.get(taskKey);
            if (line == null) {
                line = new InvoiceLineDTO(
                        projectId,
                        entryProject.getName(),
                        task != null ? task.getId() : null,
                        task != null ? task.getName() : "Allgemeine Projektarbeit",
                        0L,
                        rateToUse
                );
                lines.put(taskKey, line);
            }
            long newMinutes = line.getMinutes() + entry.getDurationMinutes();
            line.setMinutes(newMinutes);
            line.recalcAmount(rateToUse);
        }

        InvoiceSummaryDTO summary = new InvoiceSummaryDTO();
        summary.setProjectId(project.getId());
        summary.setProjectName(project.getName());
        summary.setCustomerName(project.getCustomer().getName());
        summary.setStartDate(start);
        summary.setEndDate(end);
        summary.setIncludeChildren(includeChildren);
        summary.setHourlyRate(project.getHourlyRate());
        summary.setOverrideRate(overrideRate);
        summary.setLineItems(new ArrayList<>(lines.values()));
        summary.setCurrency(currency != null ? currency : "CHF");

        long totalMinutes = summary.getLineItems().stream().mapToLong(InvoiceLineDTO::getMinutes).sum();
        summary.setTotalBillableMinutes(totalMinutes);
        BigDecimal totalAmount = summary.getLineItems().stream()
                .map(InvoiceLineDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        summary.setTotalAmount(totalAmount);

        complianceAuditService.recordAction(actor, "GENERATE", "BILLING", project.getId(),
                String.format("Automatisierte Abrechnung für %s (%s - %s)",
                        project.getName(), start, end));
        accountsReceivableService.recordProjectInvoice(project, summary);
        return summary;
    }
}
