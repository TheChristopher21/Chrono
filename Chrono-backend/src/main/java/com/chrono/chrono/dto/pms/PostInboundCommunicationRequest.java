package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.CommunicationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostInboundCommunicationRequest(
        @NotNull Long guestId,
        Long reservationId,
        @NotNull CommunicationChannel channel,
        @NotBlank @Size(max = 190) String sender,
        @Size(max = 240) String subject,
        @NotBlank @Size(max = 8000) String body,
        @Size(max = 190) String externalThreadId
) {
}
