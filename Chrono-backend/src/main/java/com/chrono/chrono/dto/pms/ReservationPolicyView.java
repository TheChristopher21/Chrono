package com.chrono.chrono.dto.pms;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
public record ReservationPolicyView(Long reservationId, LocalDateTime snapshotAt, BigDecimal depositPercent,
        BigDecimal depositRequiredAmount, BigDecimal paidAmount, BigDecimal depositOutstandingAmount,
        LocalDate depositDueDate, boolean depositOverdue, LocalDateTime cancellationDeadline,
        BigDecimal cancellationFeeNow, BigDecimal noShowFee, BigDecimal policyFeeTaxRate,
        boolean financialCorrectionRequired) {}
