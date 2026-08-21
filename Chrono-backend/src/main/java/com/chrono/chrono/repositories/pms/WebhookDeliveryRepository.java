package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    boolean existsByConnection_IdAndDeliveryId(Long connectionId, String deliveryId);

    @Modifying
    @Query("delete from WebhookDelivery delivery where delivery.receivedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
