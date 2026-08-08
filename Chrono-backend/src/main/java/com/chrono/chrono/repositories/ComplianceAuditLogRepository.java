package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.ComplianceAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

import java.util.List;

public interface ComplianceAuditLogRepository extends JpaRepository<ComplianceAuditLog, Long> {
    List<ComplianceAuditLog> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM ComplianceAuditLog log WHERE log.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") Long companyId);
}
