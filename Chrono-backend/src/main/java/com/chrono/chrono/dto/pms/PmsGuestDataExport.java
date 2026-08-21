package com.chrono.chrono.dto.pms;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PmsGuestDataExport(
        LocalDateTime generatedAt,
        String generatedBy,
        GuestData guest,
        List<ReservationData> reservations,
        List<CommunicationData> communications,
        List<RegistrationData> registrations,
        List<InvoiceData> retainedInvoices,
        List<AuditData> auditTrail,
        String legalRetentionNotice
) {
    public record GuestData(
            Long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate dateOfBirth,
            String nationalityCode,
            String languageCode,
            String notes,
            boolean vip,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ReservationData(
            Long id,
            Long propertyId,
            String propertyName,
            String confirmationCode,
            LocalDate arrivalDate,
            LocalDate departureDate,
            String status,
            String source,
            String currencyCode,
            String totalAmount,
            String notes
    ) {
    }

    public record CommunicationData(
            Long id,
            Long propertyId,
            String channel,
            String direction,
            String recipient,
            String sender,
            String subject,
            String body,
            String status,
            LocalDateTime createdAt
    ) {
    }

    public record RegistrationData(
            Long id,
            Long reservationId,
            String status,
            String addressLine,
            String postalCode,
            String city,
            String countryCode,
            String nationalityCode,
            String documentLastFour,
            String vehiclePlate,
            String signatureName,
            LocalDateTime privacyConsentAt,
            LocalDateTime completedAt
    ) {
    }

    public record InvoiceData(
            Long id,
            String invoiceNumber,
            String type,
            LocalDate issueDate,
            String recipientName,
            String currencyCode,
            String grossAmount,
            String status
    ) {
    }

    public record AuditData(
            Long id,
            String actor,
            String eventType,
            String aggregateType,
            String aggregateId,
            LocalDateTime createdAt,
            String integrityHash
    ) {
    }
}
