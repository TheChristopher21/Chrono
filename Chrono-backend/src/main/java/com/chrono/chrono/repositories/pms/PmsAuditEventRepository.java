package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PmsAuditEventRepository extends JpaRepository<PmsAuditEvent, Long> {
    List<PmsAuditEvent> findTop100ByProperty_IdOrderByCreatedAtDesc(Long propertyId);
    List<PmsAuditEvent> findTop100ByProperty_IdOrderBySequenceNumberDescCreatedAtDesc(Long propertyId);
    List<PmsAuditEvent> findTop100ByCompany_IdAndAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            Long companyId, String aggregateType, String aggregateId);

    Optional<PmsAuditEvent> findFirstByProperty_IdAndSequenceNumberIsNotNullOrderBySequenceNumberDesc(Long propertyId);

    @Query("select max(event.sequenceNumber) from PmsAuditEvent event where event.property.id = :propertyId")
    Long findMaximumSequence(@Param("propertyId") Long propertyId);
}
