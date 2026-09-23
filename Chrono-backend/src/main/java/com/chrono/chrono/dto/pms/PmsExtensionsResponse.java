package com.chrono.chrono.dto.pms;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public record PmsExtensionsResponse(
        BookingSettingsView bookingEngine,
        TourismTaxRuleView tourismTax,
        List<PosTicketView> posTickets,
        List<AccessCredentialView> accessCredentials,
        List<MigrationBatchView> migrationBatches
) {
    public record BookingSettingsView(Long id, String publicSlug, boolean enabled, boolean requireGuarantee,
                                      String termsUrl, String privacyUrl, String confirmationMessage) {}
    public record TourismTaxRuleView(Long id, boolean enabled, String name, BigDecimal adultRate,
                                     BigDecimal childRate, int childFreeUnder, Integer maximumNights) {}
    public record PosLineView(String description, BigDecimal quantity, BigDecimal unitPrice,
                              BigDecimal taxRate, BigDecimal grossAmount) {}
    public record PosTicketView(Long id, String ticketNumber, String outletCode, String tableReference,
                                LocalDate serviceDate, String status, String paymentMethod, Long folioId, String guestName,
                                String currencyCode, BigDecimal netAmount, BigDecimal taxAmount,
                                BigDecimal grossAmount, LocalDateTime createdAt, List<PosLineView> lines) {}
    public record AccessCredentialView(Long id, Long reservationId, String confirmationCode,
                                       String guestName, String roomNumber, String providerCode,
                                       String externalReference, String status, LocalDateTime validFrom,
                                       LocalDateTime validUntil, LocalDateTime issuedAt) {}
    public record MigrationBatchView(Long id, String idempotencyKey, String sourceSystem, String status,
                                     int importedGuests, int importedReservations, int importedPayments,
                                     BigDecimal totalOpeningBalance, String reconciliationMessage,
                                     LocalDateTime createdAt, LocalDateTime completedAt) {}
}
