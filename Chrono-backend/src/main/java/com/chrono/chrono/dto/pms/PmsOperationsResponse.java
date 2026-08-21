package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PmsOperationsResponse(
        Long propertyId,
        String propertyName,
        String currencyCode,
        LocalDate businessDate,
        MetricsView metrics,
        List<ReservationView> reservations,
        List<ReservationView> arrivals,
        List<ReservationView> departures,
        List<GuestView> guests,
        List<RatePlanView> ratePlans,
        List<RateOverrideView> rateOverrides,
        List<RoomStateView> rooms,
        List<HousekeepingTaskView> housekeepingTasks,
        List<FolioView> folios,
        CashShiftView cashShift,
        List<RoomBlockView> roomBlocks,
        List<MaintenanceWorkOrderView> maintenanceWorkOrders
) {
    public record MetricsView(
            long totalRooms,
            long occupiedRooms,
            long availableRooms,
            int occupancyPercent,
            long inHouse,
            long arrivals,
            long departures,
            long dirtyRooms,
            long openFolios,
            BigDecimal openBalance
    ) {
    }

    public record ReservationView(
            Long id,
            long version,
            String confirmationCode,
            Long groupBookingId,
            String groupName,
            Long guestId,
            String guestName,
            String guestEmail,
            Long roomTypeId,
            String roomTypeName,
            Long roomId,
            String roomNumber,
            Long ratePlanId,
            String ratePlanName,
            LocalDate arrivalDate,
            LocalDate departureDate,
            int adults,
            int children,
            ReservationStatus status,
            ReservationSource source,
            ReservationGuaranteeStatus guaranteeStatus,
            LocalDateTime holdUntil,
            BigDecimal totalAmount,
            String currencyCode,
            String notes,
            LocalDateTime checkedInAt,
            LocalDateTime checkedOutAt,
            LocalDateTime cancelledAt,
            LocalDateTime noShowAt,
            String cancellationReason,
            List<ReservationHistoryView> history
    ) {
    }

    public record ReservationHistoryView(
            Long id,
            ReservationStatus fromStatus,
            ReservationStatus toStatus,
            String changedBy,
            LocalDateTime changedAt,
            String reason
    ) {
    }

    public record GuestView(
            Long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate dateOfBirth,
            String nationalityCode,
            String languageCode,
            String notes,
            boolean vip
    ) {
    }

    public record RatePlanView(
            Long id,
            Long roomTypeId,
            String roomTypeName,
            String code,
            String name,
            String currencyCode,
            BigDecimal nightlyRate,
            int minStay,
            boolean breakfastIncluded,
            boolean refundable,
            boolean active
    ) {
    }

    public record RateOverrideView(
            Long id,
            Long ratePlanId,
            LocalDate stayDate,
            BigDecimal price,
            int minStay,
            boolean closed,
            boolean closedArrival,
            boolean closedDeparture
    ) {
    }

    public record RoomStateView(
            Long id,
            Long roomTypeId,
            String roomTypeName,
            String number,
            String floor,
            RoomOperationalStatus operationalStatus,
            HousekeepingStatus housekeepingStatus,
            ReservationView currentReservation
    ) {
    }

    public record HousekeepingTaskView(
            Long id,
            Long roomId,
            String roomNumber,
            LocalDate serviceDate,
            HousekeepingTaskType type,
            HousekeepingStatus status,
            int priority,
            int estimatedMinutes,
            String notes,
            String assignedTo,
            LocalDateTime completedAt
    ) {
    }

    public record FolioView(
            Long id,
            Long reservationId,
            String confirmationCode,
            String guestName,
            String label,
            Long organizationId,
            String organizationName,
            String currencyCode,
            FolioStatus status,
            BigDecimal charges,
            BigDecimal payments,
            BigDecimal balance,
            List<FolioItemView> items,
            List<PaymentView> paymentEntries
    ) {
    }

    public record FolioItemView(
            Long id,
            LocalDate serviceDate,
            FolioItemType type,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount
    ) {
    }

    public record PaymentView(
            Long id,
            BigDecimal amount,
            PaymentMethod method,
            PaymentStatus status,
            PaymentKind kind,
            Long originalPaymentId,
            String reference,
            String reason,
            LocalDateTime receivedAt,
            String createdBy,
            LocalDateTime voidedAt,
            String voidedBy
    ) {
    }

    public record CashShiftView(
            Long id,
            CashShiftStatus status,
            String openedBy,
            LocalDateTime openedAt,
            BigDecimal openingFloat,
            BigDecimal cashMovements,
            BigDecimal expectedCash,
            BigDecimal actualCash,
            BigDecimal variance,
            String closedBy,
            LocalDateTime closedAt,
            String notes
    ) {
    }

    public record RoomBlockView(
            Long id,
            Long roomId,
            String roomNumber,
            RoomBlockType type,
            RoomBlockStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            String createdBy,
            LocalDateTime createdAt,
            String resolvedBy,
            LocalDateTime resolvedAt
    ) {
    }

    public record MaintenanceWorkOrderView(
            Long id,
            Long roomId,
            String roomNumber,
            Long roomBlockId,
            String title,
            String description,
            MaintenancePriority priority,
            MaintenanceStatus status,
            String assignedTo,
            LocalDate dueDate,
            String reportedBy,
            LocalDateTime reportedAt,
            String resolutionNotes,
            String resolvedBy,
            LocalDateTime resolvedAt
    ) {
    }
}
