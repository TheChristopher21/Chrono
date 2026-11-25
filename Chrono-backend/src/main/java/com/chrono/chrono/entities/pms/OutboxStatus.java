package com.chrono.chrono.entities.pms;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED,
    DEAD_LETTER
}
