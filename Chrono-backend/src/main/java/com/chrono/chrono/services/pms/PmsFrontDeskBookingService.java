package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.CreateFrontDeskBookingRequest;
import com.chrono.chrono.dto.pms.FrontDeskBookingResponse;
import com.chrono.chrono.dto.pms.PmsOperationsResponse;
import com.chrono.chrono.dto.pms.UpsertReservationRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.Folio;
import com.chrono.chrono.entities.pms.FrontDeskBookingRequestRecord;
import com.chrono.chrono.entities.pms.GuestProfile;
import com.chrono.chrono.entities.pms.GuestRegistrationStatus;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.PaymentStatus;
import com.chrono.chrono.entities.pms.Reservation;
import com.chrono.chrono.entities.pms.ReservationSource;
import com.chrono.chrono.entities.pms.ReservationStatus;
import com.chrono.chrono.repositories.pms.FolioItemRepository;
import com.chrono.chrono.repositories.pms.FolioRepository;
import com.chrono.chrono.repositories.pms.FrontDeskBookingRequestRepository;
import com.chrono.chrono.repositories.pms.GuestProfileRepository;
import com.chrono.chrono.repositories.pms.GuestRegistrationRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class PmsFrontDeskBookingService {
    private static final Set<ReservationSource> FRONT_DESK_SOURCES = EnumSet.of(
            ReservationSource.DIRECT,
            ReservationSource.PHONE,
            ReservationSource.EMAIL,
            ReservationSource.WALK_IN
    );

    private final HotelPropertyRepository propertyRepository;
    private final GuestProfileRepository guestRepository;
    private final FrontDeskBookingRequestRepository requestRepository;
    private final GuestRegistrationRepository registrationRepository;
    private final FolioRepository folioRepository;
    private final FolioItemRepository folioItemRepository;
    private final PaymentRepository paymentRepository;
    private final PmsOperationsService operationsService;
    private final PmsAdvancedService advancedService;

    public PmsFrontDeskBookingService(
            HotelPropertyRepository propertyRepository,
            GuestProfileRepository guestRepository,
            FrontDeskBookingRequestRepository requestRepository,
            GuestRegistrationRepository registrationRepository,
            FolioRepository folioRepository,
            FolioItemRepository folioItemRepository,
            PaymentRepository paymentRepository,
            PmsOperationsService operationsService,
            PmsAdvancedService advancedService) {
        this.propertyRepository = propertyRepository;
        this.guestRepository = guestRepository;
        this.requestRepository = requestRepository;
        this.registrationRepository = registrationRepository;
        this.folioRepository = folioRepository;
        this.folioItemRepository = folioItemRepository;
        this.paymentRepository = paymentRepository;
        this.operationsService = operationsService;
        this.advancedService = advancedService;
    }

    @Transactional
    public FrontDeskBookingResponse createBooking(
            Company company,
            Long propertyId,
            String idempotencyKey,
            CreateFrontDeskBookingRequest request,
            String username,
            LocalDate businessDate) {
        requireCompany(company);
        validateRequest(request);
        String key = validateIdempotencyKey(idempotencyKey);
        String fingerprint = fingerprint(request);
        HotelProperty property = propertyRepository.findByIdAndCompany_IdForUpdate(propertyId, company.getId())
                .orElseThrow(() -> notFound("Hotel nicht gefunden."));

        FrontDeskBookingRequestRecord previous = requestRepository
                .findByProperty_IdAndIdempotencyKey(propertyId, key)
                .orElse(null);
        if (previous != null) {
            if (!previous.getRequestFingerprint().equals(fingerprint)) {
                throw conflict("Der Idempotency-Key wurde bereits für eine andere Rezeptionsbuchung verwendet.");
            }
            return response(company, previous.getReservation(), businessDate, null);
        }

        GuestProfile guest = request.existingGuestId() == null
                ? operationsService.createGuestRecord(company, propertyId, request.newGuest())
                : guestRepository.findByIdAndCompany_Id(request.existingGuestId(), company.getId())
                        .orElseThrow(() -> notFound("Gast nicht gefunden."));

        Reservation reservation = operationsService.createReservationRecord(
                company,
                new UpsertReservationRequest(
                        propertyId,
                        guest.getId(),
                        request.roomTypeId(),
                        request.roomId(),
                        request.ratePlanId(),
                        request.arrivalDate(),
                        request.departureDate(),
                        request.adults(),
                        request.children(),
                        ReservationStatus.CONFIRMED,
                        request.source(),
                        request.notes(),
                        request.guaranteeStatus(),
                        null,
                        request.childAges()
                ),
                actor(username)
        );

        if (request.registration() != null) {
            advancedService.completeGuestRegistrationRecord(
                    company,
                    property,
                    reservation.getId(),
                    request.registration(),
                    actor(username)
            );
        }

        FrontDeskBookingRequestRecord storedRequest = new FrontDeskBookingRequestRecord();
        storedRequest.setProperty(property);
        storedRequest.setReservation(reservation);
        storedRequest.setIdempotencyKey(key);
        storedRequest.setRequestFingerprint(fingerprint);
        storedRequest.setCreatedBy(actor(username));
        requestRepository.saveAndFlush(storedRequest);

        PmsOperationsResponse operations = request.checkInNow()
                ? operationsService.checkIn(company, reservation.getId(), actor(username), businessDate)
                : operationsService.getOperations(company, propertyId, businessDate, null, null);
        return response(company, reservation, businessDate, operations);
    }

    private FrontDeskBookingResponse response(
            Company company,
            Reservation reservation,
            LocalDate businessDate,
            PmsOperationsResponse loadedOperations) {
        PmsOperationsResponse operations = loadedOperations == null
                ? operationsService.getOperations(
                        company,
                        reservation.getProperty().getId(),
                        businessDate,
                        null,
                        null
                )
                : loadedOperations;
        Folio folio = folioRepository.findFirstByReservation_IdOrderByIdAsc(reservation.getId())
                .orElseThrow(() -> conflict("Zur Rezeptionsbuchung fehlt das Gastkonto."));
        BigDecimal charges = folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folio.getId()).stream()
                .map(item -> item.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payments = paymentRepository.findAllByFolio_IdOrderByReceivedAtAsc(folio.getId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.POSTED)
                .map(payment -> payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        GuestRegistrationStatus registrationStatus = registrationRepository
                .findByReservation_Id(reservation.getId())
                .map(registration -> registration.getStatus())
                .orElse(GuestRegistrationStatus.PENDING);
        List<String> checkInBlockers = new ArrayList<>();
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN) {
            if (registrationStatus != GuestRegistrationStatus.COMPLETED) {
                checkInBlockers.add("Vor dem Check-in müssen die Meldedaten vollständig erfasst werden.");
            }
            checkInBlockers.addAll(operationsService.checkInBlockers(reservation));
        }
        return new FrontDeskBookingResponse(
                reservation.getGuest().getId(),
                reservation.getId(),
                folio.getId(),
                reservation.getConfirmationCode(),
                money(reservation.getTotalAmount()),
                money(charges.subtract(payments)),
                registrationStatus,
                reservation.getStatus(),
                checkInBlockers.isEmpty(),
                List.copyOf(checkInBlockers),
                operations
        );
    }

    private void validateRequest(CreateFrontDeskBookingRequest request) {
        if (request == null) {
            throw badRequest("Die Rezeptionsbuchung fehlt.");
        }
        if ((request.existingGuestId() == null) == (request.newGuest() == null)) {
            throw badRequest("Genau ein bestehender oder neuer Gast muss angegeben werden.");
        }
        if (request.newGuest() != null
                && clean(request.newGuest().email()) == null
                && clean(request.newGuest().phone()) == null) {
            throw badRequest("Für einen neuen Gast muss eine E-Mail-Adresse oder Telefonnummer angegeben werden.");
        }
        if (request.source() == null || !FRONT_DESK_SOURCES.contains(request.source())) {
            throw badRequest("Die Buchungsquelle ist für eine Rezeptionsbuchung nicht zulässig.");
        }
        if ((request.source() == ReservationSource.WALK_IN) != request.checkInNow()) {
            throw badRequest("WALK_IN und sofortiger Check-in müssen gemeinsam gewählt werden.");
        }
        if (request.checkInNow() && request.registration() == null) {
            throw badRequest("Für einen direkten Check-in müssen die Meldedaten vollständig vorliegen.");
        }
        if (request.checkInNow() && request.roomId() == null) {
            throw badRequest("Für einen direkten Check-in muss ein Zimmer zugewiesen sein.");
        }
    }

    private String validateIdempotencyKey(String value) {
        String key = clean(value);
        if (key == null || !key.matches("^[A-Za-z0-9._:-]{16,80}$")) {
            throw badRequest("Der Idempotency-Key muss 16 bis 80 sichere Zeichen enthalten.");
        }
        return key;
    }

    private String fingerprint(CreateFrontDeskBookingRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, request.existingGuestId());
            if (request.newGuest() == null) {
                add(digest, null);
            } else {
                add(digest, request.newGuest().firstName());
                add(digest, request.newGuest().lastName());
                add(digest, request.newGuest().email());
                add(digest, request.newGuest().phone());
                add(digest, request.newGuest().dateOfBirth());
                add(digest, request.newGuest().nationalityCode());
                add(digest, request.newGuest().languageCode());
                add(digest, request.newGuest().notes());
                add(digest, request.newGuest().vip());
                add(digest, request.newGuest().addressLine1());
                add(digest, request.newGuest().postalCode());
                add(digest, request.newGuest().city());
                add(digest, request.newGuest().countryCode());
                add(digest, request.newGuest().vehiclePlate());
                add(digest, request.newGuest().roomPreferences());
                add(digest, request.newGuest().organizationId());
            }
            add(digest, request.roomTypeId());
            add(digest, request.roomId());
            add(digest, request.ratePlanId());
            add(digest, request.arrivalDate());
            add(digest, request.departureDate());
            add(digest, request.adults());
            add(digest, request.children());
            add(digest, request.childAges());
            add(digest, request.source());
            add(digest, request.guaranteeStatus());
            add(digest, request.notes());
            if (request.registration() == null) {
                add(digest, null);
            } else {
                add(digest, request.registration().addressLine());
                add(digest, request.registration().postalCode());
                add(digest, request.registration().city());
                add(digest, request.registration().countryCode());
                add(digest, request.registration().nationalityCode());
                add(digest, request.registration().documentNumber());
                add(digest, request.registration().vehiclePlate());
                add(digest, request.registration().signatureName());
                add(digest, request.registration().privacyConsent());
                add(digest, request.registration().acknowledgedRuleCode());
                add(digest, request.registration().acknowledgedRuleVersion());
            }
            add(digest, request.checkInNow());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private void add(MessageDigest digest, Object value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String actor(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "system" : cleaned;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireCompany(Company company) {
        if (company == null || company.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
