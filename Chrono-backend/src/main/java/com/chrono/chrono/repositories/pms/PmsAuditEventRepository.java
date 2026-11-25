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

    @Query("select event.property.id, max(event.sequenceNumber) from PmsAuditEvent event where event.company.id = :companyId and event.property.id in :propertyIds group by event.property.id")
    List<Object[]> liveSequences(@Param("companyId") Long companyId, @Param("propertyIds") java.util.Collection<Long> propertyIds);
}
