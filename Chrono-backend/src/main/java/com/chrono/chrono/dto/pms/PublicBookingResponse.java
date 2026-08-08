package com.chrono.chrono.dto.pms;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PublicBookingResponse(
        String confirmationCode,
        String hotelName,
        String roomTypeName,
        String rateName,
        String currencyCode,
        BigDecimal totalAmount,
        String confirmationMessage,
        String status,
        boolean verificationRequired,
        LocalDateTime holdUntil
) {}
