package com.chrono.chrono.dto.pms;

import java.time.LocalDateTime;
import java.util.List;

public record PmsOperationalHealthResponse(
        Long propertyId,
        HealthStatus status,
        LocalDateTime checkedAt,
        long pendingEvents,
        long failedEvents,
        long deadLetterEvents,
        List<ComponentHealth> components,
        List<OperationalAlert> alerts
) {
    public enum HealthStatus {
        OK,
        WARNING,
        CRITICAL,
        NOT_CONFIGURED
    }

    public record ComponentHealth(
            String key,
            String label,
            HealthStatus status,
            String summary,
            LocalDateTime observedAt
    ) {
    }

    public record OperationalAlert(
            String code,
            HealthStatus severity,
            String title,
            String details,
            String recommendedAction
    ) {
    }
}
