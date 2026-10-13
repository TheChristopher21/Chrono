package com.chrono.chrono.dto.pms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExternalBookingRequest(
        @NotBlank @Size(max = 50) String channel,
        @NotBlank @Size(max = 160) String externalId,
        @NotNull @Valid UpsertReservationRequest reservation
) {
}
