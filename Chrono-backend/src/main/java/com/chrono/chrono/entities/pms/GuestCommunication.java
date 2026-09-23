package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_guest_communications", indexes = @Index(
        name = "idx_pms_communication_property_status",
        columnList = "property_id,status"
))
public class GuestCommunication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private GuestProfile guest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(nullable = false, length = 190)
    private String recipient;

    @Column(length = 190)
    private String sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationChannel channel = CommunicationChannel.EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationDirection direction = CommunicationDirection.OUTBOUND;

    @Column(name = "external_thread_id", length = 190)
    private String externalThreadId;

    @Column(nullable = false, length = 240)
    private String subject;

    @Column(nullable = false, length = 8000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationStatus status = CommunicationStatus.QUEUED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
