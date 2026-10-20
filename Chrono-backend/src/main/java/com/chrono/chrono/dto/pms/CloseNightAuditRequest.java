package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CloseNightAuditRequest(
        @NotNull LocalDate businessDate,
        boolean markPendingArrivalsAsNoShow
) {
}
