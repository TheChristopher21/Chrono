package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QueueCommunicationRequest(
        @NotNull Long guestId,
        Long reservationId,
        @NotNull Long templateId,
        @Email @Size(max = 190) String recipient
) {
}
