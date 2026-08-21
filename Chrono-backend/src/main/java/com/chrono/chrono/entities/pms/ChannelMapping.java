package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "pms_channel_mappings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_channel_mapping_rate",
                columnNames = {"connection_id", "rate_plan_id"}
        )
)
public class ChannelMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private ChannelConnection connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rate_plan_id", nullable = false)
    private RatePlan ratePlan;

    @Column(name = "external_room_code", nullable = false, length = 100)
    private String externalRoomCode;

    @Column(name = "external_rate_code", nullable = false, length = 100)
    private String externalRateCode;

    @Column(nullable = false)
    private boolean active = true;
}
