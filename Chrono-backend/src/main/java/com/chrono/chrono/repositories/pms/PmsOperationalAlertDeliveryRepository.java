package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsOperationalAlertDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PmsOperationalAlertDeliveryRepository extends JpaRepository<PmsOperationalAlertDelivery, Long> {
    List<PmsOperationalAlertDelivery> findAllByProperty_Id(Long propertyId);

    @Modifying
    @Query("""
            update PmsOperationalAlertDelivery d
            set d.notifiedSeverity = case when d.notifiedSeverity < :severity then :severity else d.notifiedSeverity end,
                d.notifiedAt = :now, d.claimToken = null, d.claimUntil = null
            where d.id = :id and d.claimToken = :token
            """)
    int acknowledge(@Param("id") Long id, @Param("token") String token,
                    @Param("severity") int severity, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            update PmsOperationalAlertDelivery d set d.claimToken = null, d.claimUntil = null
            where d.id = :id and d.claimToken = :token
            """)
    int release(@Param("id") Long id, @Param("token") String token);
}
