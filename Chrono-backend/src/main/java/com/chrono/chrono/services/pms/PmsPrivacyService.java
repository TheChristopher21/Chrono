package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.AnonymizeGuestResponse;
import com.chrono.chrono.dto.pms.PmsGuestDataExport;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PmsPrivacyService {
    private static final Set<ReservationStatus> ANONYMIZABLE_RESERVATION_STATUSES =
            Set.of(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW, ReservationStatus.CHECKED_OUT);
    private static final String RETENTION_NOTICE =
            "Rechnungen, Buchungsreferenzen und steuerrechtlich erforderliche Beträge bleiben "
                    + "für gesetzliche Aufbewahrungspflichten erhalten.";

    private final GuestProfileRepository guestRepository;
    private final HotelPropertyRepository propertyRepository;
    private final ReservationRepository reservationRepository;
    private final FolioRepository folioRepository;
    private final PmsInvoiceRepository invoiceRepository;
    private final GuestCommunicationRepository communicationRepository;
    private final GuestRegistrationRepository registrationRepository;
    private final PmsAuditEventRepository auditRepository;
    private final PmsAuditWriter auditWriter;

    public PmsPrivacyService(GuestProfileRepository guestRepository,
                             HotelPropertyRepository propertyRepository,
                             ReservationRepository reservationRepository,
                             FolioRepository folioRepository,
                             PmsInvoiceRepository invoiceRepository,
                             GuestCommunicationRepository communicationRepository,
                             GuestRegistrationRepository registrationRepository,
                             PmsAuditEventRepository auditRepository,
                             PmsAuditWriter auditWriter) {
        this.guestRepository = guestRepository;
        this.propertyRepository = propertyRepository;
        this.reservationRepository = reservationRepository;
        this.folioRepository = folioRepository;
        this.invoiceRepository = invoiceRepository;
        this.communicationRepository = communicationRepository;
        this.registrationRepository = registrationRepository;
        this.auditRepository = auditRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public PmsGuestDataExport exportGuestData(Company company, Long guestId, String username) {
        GuestProfile guest = requireGuest(company, guestId);
        List<Reservation> reservations =
                reservationRepository.findAllByGuest_IdOrderByArrivalDateDesc(guestId);
        List<GuestCommunication> communications =
                communicationRepository.findAllByGuest_IdOrderByCreatedAtDesc(guestId);
        List<GuestRegistration> registrations = reservations.stream()
                .map(reservation -> registrationRepository.findByReservation_Id(reservation.getId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<PmsInvoice> invoices = invoices(reservations);
        List<PmsAuditEvent> auditEvents =
                auditRepository.findTop100ByCompany_IdAndAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
                        company.getId(), "guest", String.valueOf(guestId));

        for (HotelProperty property : auditProperties(company, reservations)) {
            auditWriter.append(property, "privacy.guest_exported", "guest", String.valueOf(guestId),
                    "{\"requestedBy\":\"" + safe(username) + "\"}");
        }

        return new PmsGuestDataExport(
                LocalDateTime.now().withNano(0),
                safe(username),
                new PmsGuestDataExport.GuestData(
                        guest.getId(), guest.getFirstName(), guest.getLastName(), guest.getEmail(),
                        guest.getPhone(), guest.getDateOfBirth(), guest.getNationalityCode(),
                        guest.getLanguageCode(), guest.getNotes(), guest.isVip(),
                        guest.getCreatedAt(), guest.getUpdatedAt()),
                reservations.stream().map(this::reservationData).toList(),
                communications.stream().map(this::communicationData).toList(),
                registrations.stream().map(this::registrationData).toList(),
                invoices.stream().map(this::invoiceData).toList(),
                auditEvents.stream().map(this::auditData).toList(),
                RETENTION_NOTICE);
    }

    @Transactional
    public AnonymizeGuestResponse anonymizeGuest(
            Company company, Long guestId, String reason, String username) {
        GuestProfile guest = requireGuest(company, guestId);
        List<Reservation> reservations =
                reservationRepository.findAllByGuest_IdOrderByArrivalDateDesc(guestId);
        if (reservations.stream()
                .anyMatch(reservation -> !ANONYMIZABLE_RESERVATION_STATUSES.contains(reservation.getStatus()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Gast kann wegen aktiver oder zukünftiger Reservierungen nicht anonymisiert werden.");
        }
        List<Folio> folios = reservations.stream()
                .flatMap(reservation -> folioRepository
                        .findAllByReservation_IdOrderByIdAsc(reservation.getId()).stream())
                .toList();
        if (folios.stream().anyMatch(folio -> folio.getStatus() != FolioStatus.CLOSED)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vor der Anonymisierung müssen alle Folios geschlossen sein.");
        }

        String anonymizedReference = "GAST-" + guestId;
        guest.setFirstName("Anonymisiert");
        guest.setLastName(anonymizedReference);
        guest.setEmail(null);
        guest.setPhone(null);
        guest.setDateOfBirth(null);
        guest.setNationalityCode(null);
        guest.setNotes(null);
        guest.setVip(false);
        guestRepository.save(guest);

        reservations.forEach(reservation -> reservation.setNotes(null));
        communicationRepository.findAllByGuest_IdOrderByCreatedAtDesc(guestId).forEach(communication -> {
            communication.setRecipient(anonymizedReference);
            communication.setSender(null);
            communication.setExternalThreadId(null);
            communication.setSubject("Anonymisierte Kommunikation");
            communication.setBody("[personenbezogene Inhalte anonymisiert]");
        });
        reservations.forEach(reservation ->
                registrationRepository.findByReservation_Id(reservation.getId()).ifPresent(registration -> {
                    registration.setAddressLine(null);
                    registration.setPostalCode(null);
                    registration.setCity(null);
                    registration.setCountryCode(null);
                    registration.setNationalityCode(null);
                    registration.setDocumentHash(null);
                    registration.setDocumentLastFour(null);
                    registration.setVehiclePlate(null);
                    registration.setSignatureName(null);
                    registration.setTokenHash(null);
                    registration.setExpiresAt(null);
                }));

        LocalDateTime anonymizedAt = LocalDateTime.now().withNano(0);
        for (HotelProperty property : auditProperties(company, reservations)) {
            auditWriter.append(property, "privacy.guest_anonymized", "guest", String.valueOf(guestId),
                    "{\"reason\":\"" + jsonSafe(reason) + "\",\"actor\":\"" + jsonSafe(username) + "\"}");
        }
        return new AnonymizeGuestResponse(guestId, true, anonymizedAt, RETENTION_NOTICE);
    }

    private List<PmsInvoice> invoices(List<Reservation> reservations) {
        List<PmsInvoice> result = new ArrayList<>();
        for (Reservation reservation : reservations) {
            for (Folio folio : folioRepository.findAllByReservation_IdOrderByIdAsc(reservation.getId())) {
                result.addAll(invoiceRepository.findAllByFolio_IdOrderByIssueDateDesc(folio.getId()));
            }
        }
        return result;
    }

    private Set<HotelProperty> properties(List<Reservation> reservations) {
        Set<HotelProperty> properties = new LinkedHashSet<>();
        reservations.forEach(reservation -> properties.add(reservation.getProperty()));
        return properties;
    }

    private Set<HotelProperty> auditProperties(Company company, List<Reservation> reservations) {
        Set<HotelProperty> properties = properties(reservations);
        if (properties.isEmpty()) {
            propertyRepository.findAllByCompany_IdOrderByNameAsc(company.getId()).stream()
                    .findFirst()
                    .ifPresent(properties::add);
        }
        if (properties.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Datenschutzaktion benötigt mindestens einen eingerichteten Hotelbetrieb für das Audit.");
        }
        return properties;
    }

    private PmsGuestDataExport.ReservationData reservationData(Reservation value) {
        return new PmsGuestDataExport.ReservationData(
                value.getId(), value.getProperty().getId(), value.getProperty().getName(),
                value.getConfirmationCode(), value.getArrivalDate(), value.getDepartureDate(),
                value.getStatus().name(), value.getSource().name(), value.getCurrencyCode(),
                value.getTotalAmount().toPlainString(), value.getNotes());
    }

    private PmsGuestDataExport.CommunicationData communicationData(GuestCommunication value) {
        return new PmsGuestDataExport.CommunicationData(
                value.getId(), value.getProperty().getId(), value.getChannel().name(),
                value.getDirection().name(), value.getRecipient(), value.getSender(),
                value.getSubject(), value.getBody(), value.getStatus().name(), value.getCreatedAt());
    }

    private PmsGuestDataExport.RegistrationData registrationData(GuestRegistration value) {
        return new PmsGuestDataExport.RegistrationData(
                value.getId(), value.getReservation().getId(), value.getStatus().name(),
                value.getAddressLine(), value.getPostalCode(), value.getCity(), value.getCountryCode(),
                value.getNationalityCode(), value.getDocumentLastFour(), value.getVehiclePlate(),
                value.getSignatureName(), value.getPrivacyConsentAt(), value.getCompletedAt());
    }

    private PmsGuestDataExport.InvoiceData invoiceData(PmsInvoice value) {
        return new PmsGuestDataExport.InvoiceData(
                value.getId(), value.getInvoiceNumber(), value.getType().name(), value.getIssueDate(),
                value.getRecipientName(), value.getCurrencyCode(), value.getGrossAmount().toPlainString(),
                value.getStatus().name());
    }

    private PmsGuestDataExport.AuditData auditData(PmsAuditEvent value) {
        return new PmsGuestDataExport.AuditData(
                value.getId(), value.getActor(), value.getEventType(), value.getAggregateType(),
                value.getAggregateId(), value.getCreatedAt(), value.getIntegrityHash());
    }

    private GuestProfile requireGuest(Company company, Long guestId) {
        return guestRepository.findByIdAndCompany_Id(guestId, company.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Gast nicht gefunden."));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "SYSTEM" : value.trim();
    }

    private String jsonSafe(String value) {
        return safe(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
