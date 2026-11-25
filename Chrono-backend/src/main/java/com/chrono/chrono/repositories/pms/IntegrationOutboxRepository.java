package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.IntegrationOutboxEvent;
import com.chrono.chrono.entities.pms.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IntegrationOutboxRepository extends JpaRepository<IntegrationOutboxEvent, Long> {
    List<IntegrationOutboxEvent> findAllByProperty_IdOrderByCreatedAtDesc(Long propertyId);
    List<IntegrationOutboxEvent> findTop100ByProperty_IdAndStatusOrderByCreatedAtAsc(Long propertyId, OutboxStatus status);
    Optional<IntegrationOutboxEvent> findByIdAndProperty_Company_Id(Long id, Long companyId);
    long countByProperty_IdAndStatus(Long propertyId, OutboxStatus status);
    Optional<IntegrationOutboxEvent> findFirstByProperty_IdAndStatusInOrderByCreatedAtAsc(
            Long propertyId, List<OutboxStatus> statuses);
    List<IntegrationOutboxEvent> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<OutboxStatus> statuses, LocalDateTime dueAt);
    List<IntegrationOutboxEvent> findTop50ByStatusAndLockedAtBeforeOrderByLockedAtAsc(
            OutboxStatus status, LocalDateTime staleBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from IntegrationOutboxEvent event where event.id = :id")
    Optional<IntegrationOutboxEvent> findLockedById(Long id);
}
