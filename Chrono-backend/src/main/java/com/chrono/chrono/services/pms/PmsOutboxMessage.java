package com.chrono.chrono.services.pms;

public record PmsOutboxMessage(
        Long id,
        Long propertyId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        int attemptNumber
) {
}
