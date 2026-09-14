package com.chrono.chrono.dto.pms;

import java.time.LocalDateTime;

public record AnonymizeGuestResponse(
        Long guestId,
        boolean anonymized,
        LocalDateTime anonymizedAt,
        String legalRetentionNotice
) {
}
