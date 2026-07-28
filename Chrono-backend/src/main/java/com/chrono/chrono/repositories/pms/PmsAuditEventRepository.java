package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmsAuditEventRepository extends JpaRepository<PmsAuditEvent, Long> {
    List<PmsAuditEvent> findTop100ByProperty_IdOrderByCreatedAtDesc(Long propertyId);
    List<PmsAuditEvent> findTop100ByCompany_IdAndAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            Long companyId, String aggregateType, String aggregateId);
}
