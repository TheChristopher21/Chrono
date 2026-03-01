package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.ComplianceAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplianceAuditLogRepository extends JpaRepository<ComplianceAuditLog, Long> {
    List<ComplianceAuditLog> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
}
