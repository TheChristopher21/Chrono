package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_webhook_deliveries", uniqueConstraints = @UniqueConstraint(
        name = "uk_pms_webhook_delivery", columnNames = {"connection_id", "delivery_id"}
))
public class WebhookDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private ChannelConnection connection;

    @Column(name = "delivery_id", nullable = false, length = 100)
    private String deliveryId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    void prePersist() {
        receivedAt = LocalDateTime.now();
    }
}
