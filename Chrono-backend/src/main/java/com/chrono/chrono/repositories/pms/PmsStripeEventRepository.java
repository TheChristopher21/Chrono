package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.PmsStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface PmsStripeEventRepository extends JpaRepository<PmsStripeEvent,Long> {
    boolean existsByEventId(String eventId);
    List<PmsStripeEvent> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(String status,LocalDateTime now);
    List<PmsStripeEvent> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(List<String> statuses,LocalDateTime now);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from PmsStripeEvent e where e.id=:id")
    java.util.Optional<PmsStripeEvent> findLocked(@org.springframework.data.repository.query.Param("id") Long id);
}
