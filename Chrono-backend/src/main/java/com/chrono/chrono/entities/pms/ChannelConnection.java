package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "pms_channel_connections",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_channel_property_provider",
                columnNames = {"property_id", "provider_code"}
        )
)
public class ChannelConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @Column(name = "provider_code", nullable = false, length = 60)
    private String providerCode;

    @Column(name = "webhook_key", length = 64, unique = true)
    private String webhookKey;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChannelEnvironment environment = ChannelEnvironment.SANDBOX;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChannelConnectionStatus status = ChannelConnectionStatus.DISABLED;

    @Column(name = "secret_reference", length = 180)
    private String secretReference;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "last_sync_message", length = 500)
    private String lastSyncMessage;
}
