package com.chrono.chrono.dto.pms;

import java.time.LocalDateTime;

public record GuestRegistrationInviteResponse(
        Long registrationId,
        Long reservationId,
        String token,
        String portalPath,
        LocalDateTime expiresAt
) {
}
