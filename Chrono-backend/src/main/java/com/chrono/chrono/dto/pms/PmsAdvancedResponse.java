package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PmsAdvancedResponse(
        Long propertyId,
        LocalDate businessDate,
        List<OrganizationView> organizations,
        List<GroupBookingView> groups,
        List<InvoiceView> invoices,
        List<NightAuditView> nightAudits,
        List<CommunicationTemplateView> communicationTemplates,
        List<GuestCommunicationView> communications,
        List<OutboxEventView> integrationOutbox,
        List<ChannelConnectionView> channelConnections,
        List<GuestRegistrationView> guestRegistrations,
        List<HotelResourceView> hotelResources,
        List<ResourceBookingView> resourceBookings,
        List<AuditEventView> auditEvents
) {
    public record OrganizationView(
            Long id,
            OrganizationType type,
            String name,
            String vatNumber,
            String addressLine1,
            String postalCode,
            String city,
            String countryCode,
            String email,
            String phone,
            String billingEmail,
            int paymentTermsDays,
            String notes,
            boolean active
    ) {
    }

    public record GroupBookingView(
            Long id,
            String groupCode,
            String name,
            LocalDate arrivalDate,
            LocalDate departureDate,
            GroupBookingStatus status,
            Long contactGuestId,
            String contactGuestName,
            Long organizationId,
            String organizationName,
            String notes,
            List<GroupMemberView> rooms
    ) {
    }

    public record GroupMemberView(
            Long reservationId,
            String confirmationCode,
            Long guestId,
            String guestName,
            Long roomTypeId,
            String roomTypeName,
            Long roomId,
            String roomNumber,
            ReservationStatus status,
            BigDecimal totalAmount
    ) {
    }

    public record InvoiceView(
            Long id,
            Long folioId,
            String invoiceNumber,
            InvoiceType type,
            Long originalInvoiceId,
            String originalInvoiceNumber,
            LocalDate issueDate,
            LocalDate dueDate,
            String recipientName,
            String currencyCode,
            BigDecimal netAmount,
            BigDecimal vatAmount,
            BigDecimal grossAmount,
            BigDecimal vatRate,
            InvoiceStatus status,
            boolean hasQrPaymentPart,
            String correctionReason,
            LocalDateTime correctedAt,
            String correctedBy
    ) {
    }

    public record NightAuditView(
            Long id,
            LocalDate businessDate,
            long arrivalsCount,
            long departuresCount,
            long inHouseCount,
            long noShowCount,
            BigDecimal openBalance,
            String closedBy,
            LocalDateTime closedAt
    ) {
    }

    public record CommunicationTemplateView(
            Long id,
            String code,
            String name,
            String subject,
            String body,
            String languageCode,
            boolean active
    ) {
    }

    public record GuestCommunicationView(
            Long id,
            Long guestId,
            String guestName,
            Long reservationId,
            String recipient,
            String sender,
            CommunicationChannel channel,
            CommunicationDirection direction,
            String externalThreadId,
            String subject,
            String body,
            CommunicationStatus status,
            LocalDateTime createdAt,
            LocalDateTime sentAt,
            LocalDateTime readAt
    ) {
    }

    public record HotelResourceView(
            Long id,
            HotelResourceType type,
            String code,
            String name,
            String location,
            int capacity,
            BigDecimal hourlyRate,
            String currencyCode,
            boolean active
    ) {
    }

    public record ResourceBookingView(
            Long id,
            Long resourceId,
            String resourceName,
            Long groupBookingId,
            String groupName,
            String title,
            String organizerName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int attendees,
            ResourceBookingStatus status,
            BigDecimal totalAmount,
            String notes,
            String createdBy,
            LocalDateTime createdAt
    ) {
    }

    public record OutboxEventView(
            Long id,
            String eventType,
            String aggregateType,
            String aggregateId,
            String payload,
            OutboxStatus status,
            int attemptCount,
            LocalDateTime createdAt,
            LocalDateTime deliveredAt,
            LocalDateTime nextAttemptAt,
            LocalDateTime lastAttemptAt,
            String lastError
    ) {
    }

    public record AuditEventView(
            Long id,
            String actor,
            String eventType,
            String aggregateType,
            String aggregateId,
            String details,
            String integrityHash,
            LocalDateTime createdAt
    ) {
    }

    public record ChannelConnectionView(
            Long id,
            String providerCode,
            String displayName,
            ChannelEnvironment environment,
            ChannelConnectionStatus status,
            String secretReference,
            LocalDateTime lastSyncAt,
            String lastSyncMessage,
            List<ChannelMappingView> mappings
    ) {
    }

    public record ChannelMappingView(
            Long id,
            Long roomTypeId,
            String roomTypeName,
            Long ratePlanId,
            String ratePlanName,
            String externalRoomCode,
            String externalRateCode,
            boolean active
    ) {
    }

    public record GuestRegistrationView(
            Long id,
            Long reservationId,
            String confirmationCode,
            String guestName,
            GuestRegistrationStatus status,
            String addressLine,
            String postalCode,
            String city,
            String countryCode,
            String nationalityCode,
            String documentLastFour,
            String vehiclePlate,
            String signatureName,
            LocalDateTime privacyConsentAt,
            LocalDateTime completedAt,
            String completedBy
    ) {
    }
}
