package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.ComplianceAuditLog;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.ComplianceAuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class ComplianceAuditService {

    @Autowired
    private ComplianceAuditLogRepository complianceAuditLogRepository;

    public void recordAction(User actor, String action, String targetType, Long targetId, String details) {
        recordAction(actor, action, targetType, targetId, details, ComplianceAuditLog.Severity.INFO);
    }

    public void recordAction(User actor, String action, String targetType, Long targetId, String details, ComplianceAuditLog.Severity severity) {
        if (actor == null || actor.getCompany() == null) {
            return;
        }
        ComplianceAuditLog log = new ComplianceAuditLog();
        log.setCompany(actor.getCompany());
        log.setUsername(actor.getUsername());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetails(details);
        log.setSeverity(severity != null ? severity : ComplianceAuditLog.Severity.INFO);
        complianceAuditLogRepository.save(log);
    }

    public void recordSystemAction(Company company, String action, String targetType, Long targetId, String details, ComplianceAuditLog.Severity severity) {
        if (company == null) {
            return;
        }
        ComplianceAuditLog log = new ComplianceAuditLog();
        log.setCompany(company);
        log.setUsername("SYSTEM");
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetails(details);
        log.setSeverity(severity != null ? severity : ComplianceAuditLog.Severity.INFO);
        complianceAuditLogRepository.save(log);
    }

    public List<ComplianceAuditLog> getRecentLogs(Long companyId, int limit) {
        if (companyId == null) {
            return Collections.emptyList();
        }
        int size = Math.max(1, Math.min(limit, 200));
        return complianceAuditLogRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(0, size));
    }
}
