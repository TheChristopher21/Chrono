package com.chrono.chrono.dto;

import java.time.LocalDateTime;

public record AnalyticsExcludedIpResponse(
        Long id,
        String ipAddress,
        String label,
        boolean configured,
        LocalDateTime createdAt
) {}
