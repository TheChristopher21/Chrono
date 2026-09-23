package com.chrono.chrono.dto.pms;

public record ChannelWebhookResponse(
        String status,
        String externalId,
        Long reservationId,
        String confirmationCode
) {}
