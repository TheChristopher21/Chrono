package com.chrono.chrono.entities.pms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "pms_public_rate_limits")
public class PublicRateLimit {
    @Id
    @Column(name = "rate_key", nullable = false, length = 64, columnDefinition = "char(64)")
    private String rateKey;

    @Column(name = "window_started_at", nullable = false)
    private LocalDateTime windowStartedAt;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
