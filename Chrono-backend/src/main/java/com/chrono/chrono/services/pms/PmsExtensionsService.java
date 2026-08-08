package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PmsExtensionsService {
    private final HotelPropertyRepository propertyRepository;
    private final BookingEngineSettingsRepository bookingSettingsRepository;
    private final TourismTaxRuleRepository tourismTaxRuleRepository;
    private final TourismTaxPostingRepository tourismTaxPostingRepository;
    private final PosTicketRepository posTicketRepository;
    private final AccessCredentialRepository accessCredentialRepository;
    private final MigrationBatchRepository migrationBatchRepository;
    private final PublicBookingRequestRepository publicBookingRequestRepository;
    private final PublicBookingVerificationRepository publicBookingVerificationRepository;
    private final ReservationRepository reservationRepository;
    private final RatePlanRepository ratePlanRepository;
    private final GuestProfileRepository guestRepository;
    private final FolioRepository folioRepository;
    private final FolioItemRepository folioItemRepository;
    private final PaymentRepository paymentRepository;
    private final ExternalBookingReferenceRepository externalBookingReferenceRepository;
    private final IntegrationOutboxRepository outboxRepository;
    private final PmsOperationsService operationsService;
    private final PmsAuditWriter auditWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration verificationTtl;
    private final int publicMaxStayNights;

    public PmsExtensionsService(
            HotelPropertyRepository propertyRepository,
            BookingEngineSettingsRepository bookingSettingsRepository,
            TourismTaxRuleRepository tourismTaxRuleRepository,
            TourismTaxPostingRepository tourismTaxPostingRepository,
            PosTicketRepository posTicketRepository,
            AccessCredentialRepository accessCredentialRepository,
            MigrationBatchRepository migrationBatchRepository,
            PublicBookingRequestRepository publicBookingRequestRepository,
            PublicBookingVerificationRepository publicBookingVerificationRepository,
            ReservationRepository reservationRepository,
            RatePlanRepository ratePlanRepository,
            GuestProfileRepository guestRepository,
            FolioRepository folioRepository,
            FolioItemRepository folioItemRepository,
            PaymentRepository paymentRepository,
            ExternalBookingReferenceRepository externalBookingReferenceRepository,
            IntegrationOutboxRepository outboxRepository,
            PmsOperationsService operationsService,
            PmsAuditWriter auditWriter,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.pms.booking-verification.ttl:PT15M}") Duration verificationTtl,
            @Value("${app.pms.public-booking.max-stay-nights:30}") int publicMaxStayNights) {
        this.propertyRepository = propertyRepository;
        this.bookingSettingsRepository = bookingSettingsRepository;
        this.tourismTaxRuleRepository = tourismTaxRuleRepository;
        this.tourismTaxPostingRepository = tourismTaxPostingRepository;
        this.posTicketRepository = posTicketRepository;
        this.accessCredentialRepository = accessCredentialRepository;
        this.migrationBatchRepository = migrationBatchRepository;
        this.publicBookingRequestRepository = publicBookingRequestRepository;
        this.publicBookingVerificationRepository = publicBookingVerificationRepository;
        this.reservationRepository = reservationRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.guestRepository = guestRepository;
        this.folioRepository = folioRepository;
        this.folioItemRepository = folioItemRepository;
        this.paymentRepository = paymentRepository;
        this.externalBookingReferenceRepository = externalBookingReferenceRepository;
        this.outboxRepository = outboxRepository;
        this.operationsService = operationsService;
        this.auditWriter = auditWriter;
        this.eventPublisher = eventPublisher;
        this.verificationTtl = verificationTtl;
        this.publicMaxStayNights = publicMaxStayNights;
    }

    @Transactional(readOnly = true)
    public PmsExtensionsResponse get(Company company, Long propertyId) {
        HotelProperty property = requireProperty(company, propertyId);
        return response(property);
    }

    @Transactional
    public PmsExtensionsResponse updateBookingSettings(Company company, Long propertyId,
                                                        PmsExtensionsRequests.BookingSettings request) {
        HotelProperty property = requireProperty(company, propertyId);
        validatePublicUrl(request.termsUrl(), "AGB-Adresse");
        validatePublicUrl(request.privacyUrl(), "Datenschutz-Adresse");
        BookingEngineSettings settings = bookingSettingsRepository.findByProperty_Id(propertyId)
                .orElseGet(BookingEngineSettings::new);
        String publicSlug = required(request.publicSlug()).toLowerCase(Locale.ROOT);
        if (bookingSettingsRepository.existsByPublicSlugIgnoreCaseAndProperty_IdNot(publicSlug, propertyId)) {
            throw conflict("Dieser öffentliche Buchungsname ist bereits vergeben.");
        }
        settings.setProperty(property);
        settings.setPublicSlug(publicSlug);
        settings.setEnabled(request.enabled());
        settings.setRequireGuarantee(request.requireGuarantee());
        settings.setTermsUrl(clean(request.termsUrl()));
        settings.setPrivacyUrl(clean(request.privacyUrl()));
        settings.setConfirmationMessage(clean(request.confirmationMessage()));
        bookingSettingsRepository.save(settings);
        auditWriter.append(property, "booking_engine.settings_updated", "booking_engine",
                String.valueOf(settings.getId()), "{\"enabled\":" + settings.isEnabled() + "}");
        return response(property);
    }

    @Transactional
    public PmsExtensionsResponse updateTourismTax(Company company, Long propertyId,
                                                  PmsExtensionsRequests.TourismTaxRuleRequest request) {
        HotelProperty property = requireProperty(company, propertyId);
        TourismTaxRule rule = tourismTaxRuleRepository.findByProperty_Id(propertyId)
                .orElseGet(TourismTaxRule::new);
        rule.setProperty(property);
        rule.setEnabled(request.enabled());
        rule.setName(required(request.name()));
        rule.setAdultRate(money(request.adultRate()));
        rule.setChildRate(money(request.childRate()));
        rule.setChildFreeUnder(request.childFreeUnder());
        rule.setMaximumNights(request.maximumNights());
        tourismTaxRuleRepository.save(rule);
        auditWriter.append(property, "tourism_tax.settings_updated", "tourism_tax_rule",
                String.valueOf(rule.getId()), "{\"enabled\":" + rule.isEnabled() + "}");
        return response(property);
    }

    @Transactional
    public PmsExtensionsResponse postTourismTax(Company company, Long propertyId,
                                                PmsExtensionsRequests.PostTourismTax request,
                                                String username) {
        HotelProperty property = requireProperty(company, propertyId);
        TourismTaxRule rule = tourismTaxRuleRepository.findByProperty_Id(propertyId)
                .filter(TourismTaxRule::isEnabled)
                .orElseThrow(() -> conflict("Die Kurtaxe ist für dieses Hotel nicht aktiviert."));
        Reservation reservation = requireReservation(company, propertyId, request.reservationId());
        if (tourismTaxPostingRepository.existsByReservation_Id(reservation.getId())) {
            throw conflict("Die Kurtaxe wurde für diese Reservierung bereits verbucht.");
        }
        if (request.chargeableChildren() > reservation.getChildren()) {
            throw badRequest("Es können nicht mehr steuerpflichtige Kinder als gebuchte Kinder erfasst werden.");
        }
        long stayNights = ChronoUnit.DAYS.between(reservation.getArrivalDate(), reservation.getDepartureDate());
        int nights = (int) Math.min(stayNights,
                rule.getMaximumNights() == null ? stayNights : rule.getMaximumNights());
        BigDecimal perNight = rule.getAdultRate().multiply(BigDecimal.valueOf(reservation.getAdults()))
                .add(rule.getChildRate().multiply(BigDecimal.valueOf(request.chargeableChildren())));
        BigDecimal amount = money(perNight.multiply(BigDecimal.valueOf(nights)));
        if (amount.signum() <= 0) {
            throw badRequest("Der berechnete Kurtaxenbetrag ist null.");
        }
        Folio folio = folioRepository.findFirstByReservation_IdOrderByIdAsc(reservation.getId())
                .filter(value -> value.getStatus() == FolioStatus.OPEN)
                .orElseThrow(() -> conflict("Für die Kurtaxe wird ein offenes Gastkonto benötigt."));
        FolioItem item = new FolioItem();
        item.setFolio(folio);
        item.setServiceDate(reservation.getArrivalDate());
        item.setType(FolioItemType.TAX);
        item.setDescription(rule.getName() + " · " + nights + " Nächte");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(amount);
        item.setTotalAmount(amount);
        folioItemRepository.save(item);
        TourismTaxPosting posting = new TourismTaxPosting();
        posting.setProperty(property);
        posting.setReservation(reservation);
        posting.setFolio(folio);
        posting.setFolioItem(item);
        posting.setAmount(amount);
        posting.setNights(nights);
        posting.setPostedBy(actor(username));
        tourismTaxPostingRepository.save(posting);
        auditWriter.append(property, "tourism_tax.posted", "reservation",
                reservation.getId().toString(), "{\"amount\":" + amount.toPlainString() + "}");
        return response(property);
    }

    @Transactional
    public PmsExtensionsResponse createPosTicket(Company company, Long propertyId,
                                                  PmsExtensionsRequests.CreatePosTicket request,
                                                  String username) {
        HotelProperty property = requireProperty(company, propertyId);
        Folio folio = null;
        if (request.folioId() != null) {
            folio = folioRepository.findByIdAndReservation_Property_Company_Id(request.folioId(), company.getId())
                    .filter(value -> value.getReservation().getProperty().getId().equals(propertyId))
                    .filter(value -> value.getStatus() == FolioStatus.OPEN)
                    .orElseThrow(() -> notFound("Offenes Gastkonto nicht gefunden."));
        } else if (request.paymentMethod() == null) {
            throw badRequest("Ein direkt bezahlter POS-Beleg benötigt eine Zahlungsart.");
        }
        PosTicket ticket = new PosTicket();
        ticket.setProperty(property);
        ticket.setFolio(folio);
        ticket.setTicketNumber(nextTicketNumber(propertyId));
        ticket.setOutletCode(required(request.outletCode()).toUpperCase(Locale.ROOT));
        ticket.setTableReference(clean(request.tableReference()));
        ticket.setServiceDate(request.serviceDate());
        ticket.setCurrencyCode(property.getCurrencyCode());
        ticket.setCreatedBy(actor(username));
        ticket.setPaymentMethod(folio == null ? request.paymentMethod() : null);

        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (PmsExtensionsRequests.PosLine input : request.lines()) {
            PosTicketLine line = new PosTicketLine();
            line.setTicket(ticket);
            line.setDescription(required(input.description()));
            line.setQuantity(input.quantity().setScale(2, RoundingMode.HALF_UP));
            line.setUnitPrice(money(input.unitPrice()));
            line.setTaxRate(input.taxRate().setScale(2, RoundingMode.HALF_UP));
            BigDecimal net = money(line.getQuantity().multiply(line.getUnitPrice()));
            BigDecimal tax = money(net.multiply(line.getTaxRate()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            line.setNetAmount(net);
            line.setTaxAmount(tax);
            line.setGrossAmount(net.add(tax));
            ticket.getLines().add(line);
            netTotal = netTotal.add(net);
            taxTotal = taxTotal.add(tax);
        }
        ticket.setNetAmount(money(netTotal));
        ticket.setTaxAmount(money(taxTotal));
        ticket.setGrossAmount(money(netTotal.add(taxTotal)));
        ticket.setStatus(PosTicketStatus.SETTLED);
        ticket.setSettledAt(LocalDateTime.now());
        posTicketRepository.save(ticket);

        if (folio != null) {
            FolioItem item = new FolioItem();
            item.setFolio(folio);
            item.setServiceDate(request.serviceDate());
            item.setType(FolioItemType.SERVICE);
            item.setDescription("POS " + ticket.getOutletCode() + " · " + ticket.getTicketNumber());
            item.setQuantity(BigDecimal.ONE);
            item.setUnitPrice(ticket.getGrossAmount());
            item.setTotalAmount(ticket.getGrossAmount());
            folioItemRepository.save(item);
        }
        auditWriter.append(property, "pos.ticket_settled", "pos_ticket", ticket.getId().toString(),
                "{\"gross\":" + ticket.getGrossAmount().toPlainString() + "}");
        return response(property);
    }

    @Transactional
    public PmsExtensionsResponse issueAccessCredential(Company company, Long propertyId,
                                                        PmsExtensionsRequests.IssueAccessCredential request,
                                                        String username) {
        HotelProperty property = requireProperty(company, propertyId);
        Reservation reservation = requireReservation(company, propertyId, request.reservationId());
        if (reservation.getRoom() == null) {
            throw conflict("Vor dem Ausstellen eines digitalen Schlüssels muss ein Zimmer zugewiesen sein.");
        }
        if (!request.validUntil().isAfter(request.validFrom())) {
            throw badRequest("Das Ende der Schlüsselgültigkeit muss nach dem Beginn liegen.");
        }
        AccessCredential credential = new AccessCredential();
        credential.setProperty(property);
        credential.setReservation(reservation);
        credential.setRoom(reservation.getRoom());
        credential.setProviderCode(required(request.providerCode()).toUpperCase(Locale.ROOT));
        credential.setExternalReference(required(request.externalReference()));
        credential.setValidFrom(request.validFrom());
        credential.setValidUntil(request.validUntil());
        credential.setStatus(AccessCredentialStatus.ACTIVE);
        credential.setIssuedBy(actor(username));
        accessCredentialRepository.save(credential);
        emit(property, "access_credential.issue_requested", "access_credential", credential.getId().toString(),
                "{\"providerCode\":\"" + json(credential.getProviderCode()) + "\",\"roomId\":"
                        + reservation.getRoom().getId() + ",\"externalReference\":\""
                        + json(credential.getExternalReference()) + "\"}");
        auditWriter.append(property, "access_credential.issued", "access_credential",
                credential.getId().toString(), "{\"roomId\":" + reservation.getRoom().getId() + "}");
        return response(property);
    }

    @Transactional
    public PmsExtensionsResponse revokeAccessCredential(Company company, Long propertyId, Long credentialId,
                                                         String username) {
        HotelProperty property = requireProperty(company, propertyId);
        AccessCredential credential = accessCredentialRepository
                .findByIdAndProperty_Company_Id(credentialId, company.getId())
                .filter(value -> value.getProperty().getId().equals(propertyId))
                .orElseThrow(() -> notFound("Digitaler Schlüssel nicht gefunden."));
        if (credential.getStatus() != AccessCredentialStatus.ACTIVE) {
            throw conflict("Der digitale Schlüssel ist nicht mehr aktiv.");
        }
        credential.setStatus(AccessCredentialStatus.REVOKED);
        credential.setRevokedAt(LocalDateTime.now());
        credential.setRevokedBy(actor(username));
        accessCredentialRepository.save(credential);
        emit(property, "access_credential.revoke_requested", "access_credential", credential.getId().toString(),
                "{\"providerCode\":\"" + json(credential.getProviderCode()) + "\",\"externalReference\":\""
                        + json(credential.getExternalReference()) + "\"}");
        auditWriter.append(property, "access_credential.revoked", "access_credential",
                credential.getId().toString(), "{}");
        return response(property);
    }

    @Transactional(readOnly = true)
    public PublicBookingConfigurationResponse publicConfiguration(String propertyCode) {
        HotelProperty property = requirePublicProperty(propertyCode);
        BookingEngineSettings settings = bookingSettingsRepository.findByProperty_Id(property.getId()).orElseThrow();
        return new PublicBookingConfigurationResponse(settings.getPublicSlug(), property.getName(), property.getCity(),
                property.getCurrencyCode(), settings.getTermsUrl(), settings.getPrivacyUrl(),
                settings.isRequireGuarantee());
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse publicAvailability(String propertyCode, LocalDate arrival, LocalDate departure) {
        HotelProperty property = requirePublicProperty(propertyCode);
        validatePublicStay(arrival, departure);
        return operationsService.getAvailability(property.getCompany(), property.getId(), arrival, departure);
    }

    @Transactional
    public PublicBookingResponse createPublicBooking(String propertyCode,
                                                      String idempotencyKey,
                                                      PmsExtensionsRequests.PublicBooking request) {
        HotelProperty publicProperty = requirePublicProperty(propertyCode);
        validatePublicStay(request.arrivalDate(), request.departureDate());
        HotelProperty property = propertyRepository.findByIdAndCompany_IdForUpdate(
                        publicProperty.getId(), publicProperty.getCompany().getId())
                .orElseThrow(() -> notFound("Hotel nicht gefunden."));
        String key = validateIdempotencyKey(idempotencyKey);
        String fingerprint = publicBookingFingerprint(request);
        BookingEngineSettings settings = bookingSettingsRepository.findByProperty_Id(property.getId()).orElseThrow();
        PublicBookingRequest existing = publicBookingRequestRepository
                .findByProperty_IdAndIdempotencyKey(property.getId(), key).orElse(null);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw conflict("Der Idempotency-Key wurde bereits für eine andere Buchung verwendet.");
            }
            return publicBookingResponse(existing.getReservation(), settings,
                    publicBookingVerificationRepository.findByBookingRequest_Id(existing.getId())
                            .map(value -> value.getVerifiedAt() == null).orElse(false));
        }
        AvailabilityResponse availability = operationsService.getAvailability(
                property.getCompany(), property.getId(), request.arrivalDate(), request.departureDate());
        AvailabilityResponse.RateOption option = availability.roomTypes().stream()
                .flatMap(roomType -> roomType.rates().stream())
                .filter(rate -> rate.ratePlanId().equals(request.ratePlanId()))
                .findFirst().filter(AvailabilityResponse.RateOption::available)
                .orElseThrow(() -> conflict("Die gewählte Rate ist für diesen Zeitraum nicht mehr buchbar."));
        RatePlan ratePlan = ratePlanRepository.findByIdAndProperty_Company_Id(request.ratePlanId(), property.getCompany().getId())
                .filter(value -> value.getProperty().getId().equals(property.getId()))
                .orElseThrow(() -> notFound("Ratenplan nicht gefunden."));
        if (request.adults() + request.children() > ratePlan.getRoomType().getMaxOccupancy()) {
            throw badRequest("Die gewählte Belegung überschreitet die maximale Zimmerbelegung.");
        }
        GuestProfile guest = new GuestProfile();
        guest.setCompany(property.getCompany());
        guest.setFirstName(required(request.firstName()));
        guest.setLastName(required(request.lastName()));
        guest.setEmail(required(request.email()).toLowerCase(Locale.ROOT));
        guest.setPhone(clean(request.phone()));
        guest.setLanguageCode("de");
        guestRepository.save(guest);
        Reservation reservation = operationsService.createReservationRecord(property.getCompany(),
                new UpsertReservationRequest(property.getId(), guest.getId(), ratePlan.getRoomType().getId(), null,
                        ratePlan.getId(), request.arrivalDate(), request.departureDate(), request.adults(),
                        request.children(), ReservationStatus.TENTATIVE, ReservationSource.BOOKING_ENGINE,
                        "Onlinebuchung", settings.isRequireGuarantee()
                        ? ReservationGuaranteeStatus.DEPOSIT_REQUIRED : ReservationGuaranteeStatus.UNGUARANTEED,
                        LocalDateTime.now().plus(verificationTtl)),
                "booking-engine");
        PublicBookingRequest bookingRequest = new PublicBookingRequest();
        bookingRequest.setProperty(property);
        bookingRequest.setReservation(reservation);
        bookingRequest.setIdempotencyKey(key);
        bookingRequest.setRequestFingerprint(fingerprint);
        publicBookingRequestRepository.saveAndFlush(bookingRequest);
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String verificationToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        PublicBookingVerification verification = new PublicBookingVerification();
        verification.setBookingRequest(bookingRequest);
        verification.setTokenHash(sha256(verificationToken));
        verification.setExpiresAt(LocalDateTime.now().plus(verificationTtl));
        publicBookingVerificationRepository.save(verification);
        eventPublisher.publishEvent(new PublicBookingVerificationRequested(
                guest.getEmail(), property.getName(), settings.getPublicSlug(),
                reservation.getConfirmationCode(), verificationToken));
        return new PublicBookingResponse(reservation.getConfirmationCode(), property.getName(),
                ratePlan.getRoomType().getName(), ratePlan.getName(), option.currencyCode(),
                reservation.getTotalAmount(), clean(settings.getConfirmationMessage()),
                reservation.getStatus().name(), true, reservation.getHoldUntil());
    }

    @Transactional
    public PublicBookingResponse verifyPublicBooking(String propertyCode, String token) {
        HotelProperty publicProperty = requirePublicProperty(propertyCode);
        propertyRepository.findByIdAndCompany_IdForUpdate(
                        publicProperty.getId(), publicProperty.getCompany().getId())
                .orElseThrow(() -> notFound("Hotel nicht gefunden."));
        PublicBookingVerification verification = publicBookingVerificationRepository
                .findByTokenHash(sha256(required(token)))
                .filter(value -> value.getBookingRequest().getProperty().getId().equals(publicProperty.getId()))
                .orElseThrow(() -> notFound("Buchungsbestätigung nicht gefunden."));
        Reservation reservation = verification.getBookingRequest().getReservation();
        if (verification.getVerifiedAt() == null && verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw conflict("Der Bestätigungslink ist abgelaufen.");
        }
        BookingEngineSettings settings = bookingSettingsRepository.findByProperty_Id(publicProperty.getId()).orElseThrow();
        if (verification.getVerifiedAt() == null) {
            reservation = operationsService.verifyPublicBookingRecord(
                    publicProperty.getCompany(), reservation.getId(), settings.isRequireGuarantee());
            verification.setVerifiedAt(LocalDateTime.now());
            publicBookingVerificationRepository.save(verification);
        }
        return publicBookingResponse(reservation, settings, false);
    }

    @Transactional
    public PmsExtensionsResponse importMigration(Company company, Long propertyId,
                                                 PmsExtensionsRequests.MigrationImport request,
                                                 String username) {
        HotelProperty property = requireProperty(company, propertyId);
        String key = required(request.idempotencyKey());
        if (migrationBatchRepository.findByProperty_IdAndIdempotencyKey(propertyId, key).isPresent()) {
            return response(property);
        }
        String sourceCode = required(request.sourceSystem()).toUpperCase(Locale.ROOT);
        sourceCode = sourceCode.substring(0, Math.min(50, sourceCode.length()));
        int guests = 0;
        int reservations = 0;
        int payments = 0;
        BigDecimal openingBalance = BigDecimal.ZERO;
        List<String> differences = new ArrayList<>();
        for (PmsExtensionsRequests.MigrationReservation row : request.reservations()) {
            if (externalBookingReferenceRepository.findByProperty_IdAndChannelCodeIgnoreCaseAndExternalId(
                    propertyId, sourceCode, required(row.externalReference())).isPresent()) {
                continue;
            }
            GuestProfile guest = new GuestProfile();
            guest.setCompany(company);
            guest.setFirstName(required(row.firstName()));
            guest.setLastName(required(row.lastName()));
            guest.setEmail(clean(row.email()));
            guest.setPhone(clean(row.phone()));
            guest.setLanguageCode("de");
            guest.setNotes("Import aus " + request.sourceSystem() + " · " + row.externalReference());
            guestRepository.save(guest);
            guests++;
            Reservation reservation = operationsService.createReservationRecord(company,
                    new UpsertReservationRequest(propertyId, guest.getId(), row.roomTypeId(), null,
                            row.ratePlanId(), row.arrivalDate(), row.departureDate(), row.adults(), row.children(),
                            ReservationStatus.CONFIRMED, ReservationSource.CHANNEL_MANAGER,
                            "Migration " + row.externalReference(), row.depositAmount().signum() > 0
                            ? ReservationGuaranteeStatus.DEPOSIT_PAID : ReservationGuaranteeStatus.UNGUARANTEED, null),
                    actor(username));
            reservations++;
            ExternalBookingReference reference = new ExternalBookingReference();
            reference.setProperty(property);
            reference.setReservation(reservation);
            reference.setChannelCode(sourceCode);
            reference.setExternalId(required(row.externalReference()));
            externalBookingReferenceRepository.save(reference);
            if (reservation.getTotalAmount().compareTo(money(row.expectedGrossAmount())) != 0) {
                differences.add(row.externalReference() + ": erwartet " + money(row.expectedGrossAmount())
                        + ", berechnet " + reservation.getTotalAmount());
            }
            Folio folio = folioRepository.findFirstByReservation_IdOrderByIdAsc(reservation.getId()).orElseThrow();
            if (row.depositAmount().signum() > 0) {
                if (row.depositAmount().compareTo(reservation.getTotalAmount()) > 0) {
                    throw badRequest("Die Anzahlung für " + row.externalReference() + " übersteigt den Aufenthaltspreis.");
                }
                Payment payment = new Payment();
                payment.setFolio(folio);
                payment.setAmount(money(row.depositAmount()));
                payment.setMethod(PaymentMethod.BANK_TRANSFER);
                payment.setStatus(PaymentStatus.POSTED);
                payment.setKind(PaymentKind.PAYMENT);
                payment.setReference("MIGRATION-" + row.externalReference());
                payment.setCreatedBy(actor(username));
                paymentRepository.save(payment);
                payments++;
            }
            openingBalance = openingBalance.add(money(row.expectedGrossAmount()).subtract(money(row.depositAmount())));
        }
        MigrationBatch batch = new MigrationBatch();
        batch.setProperty(property);
        batch.setIdempotencyKey(key);
        batch.setSourceSystem(required(request.sourceSystem()));
        batch.setImportedGuests(guests);
        batch.setImportedReservations(reservations);
        batch.setImportedPayments(payments);
        batch.setTotalOpeningBalance(money(openingBalance));
        batch.setStatus(differences.isEmpty() ? MigrationBatchStatus.COMPLETED
                : MigrationBatchStatus.RECONCILIATION_REQUIRED);
        batch.setReconciliationMessage(differences.isEmpty() ? "Counts und Eröffnungssalden stimmen."
                : String.join("; ", differences).substring(0, Math.min(1000, String.join("; ", differences).length())));
        batch.setCreatedBy(actor(username));
        batch.setCompletedAt(LocalDateTime.now());
        migrationBatchRepository.save(batch);
        auditWriter.append(property, "migration.batch_completed", "migration_batch", batch.getId().toString(),
                "{\"reservations\":" + reservations + ",\"status\":\"" + batch.getStatus() + "\"}");
        return response(property);
    }

    @Transactional(readOnly = true)
    public byte[] accountingExport(Company company, Long propertyId, LocalDate from, LocalDate toExclusive) {
        HotelProperty property = requireProperty(company, propertyId);
        if (from == null || toExclusive == null || !toExclusive.isAfter(from)) {
            throw badRequest("Der Exportzeitraum ist ungültig.");
        }
        StringBuilder csv = new StringBuilder("date;document;debitAccount;creditAccount;description;debitAmount;creditAmount;currency;taxRate\n");
        folioItemRepository
                .findAllByFolio_Reservation_Property_IdAndServiceDateGreaterThanEqualAndServiceDateLessThanOrderByServiceDateAscIdAsc(
                        propertyId, from, toExclusive)
                .forEach(item -> appendPosting(csv, item.getServiceDate().toString(), "FOLIO-" + item.getFolio().getId()
                                + "-" + item.getId(), "1100", revenueAccount(item.getType()), item.getDescription(),
                        item.getTotalAmount(), property.getCurrencyCode(), BigDecimal.ZERO));
        paymentRepository
                .findAllByFolio_Reservation_Property_IdAndReceivedAtGreaterThanEqualAndReceivedAtLessThanOrderByReceivedAtAsc(
                        propertyId, from.atStartOfDay(), toExclusive.atStartOfDay())
                .stream().filter(payment -> payment.getStatus() == PaymentStatus.POSTED)
                .forEach(payment -> appendPosting(csv, payment.getReceivedAt().toLocalDate().toString(),
                        "PAY-" + payment.getId(), paymentAccount(payment.getMethod()), "1100",
                        "Zahlung " + payment.getFolio().getReservation().getConfirmationCode(),
                        payment.getAmount(), property.getCurrencyCode(), BigDecimal.ZERO));
        posTicketRepository
                .findAllByProperty_IdAndServiceDateGreaterThanEqualAndServiceDateLessThanOrderByServiceDateAscCreatedAtAsc(
                        propertyId, from, toExclusive)
                .stream().filter(ticket -> ticket.getFolio() == null && ticket.getPaymentMethod() != null)
                .forEach(ticket -> ticket.getLines().forEach(line -> appendPosting(csv,
                        ticket.getServiceDate().toString(), ticket.getTicketNumber(),
                        paymentAccount(ticket.getPaymentMethod()), "3220", line.getDescription(),
                        line.getGrossAmount(), ticket.getCurrencyCode(), line.getTaxRate())));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private PmsExtensionsResponse response(HotelProperty property) {
        BookingEngineSettings settings = bookingSettingsRepository.findByProperty_Id(property.getId()).orElse(null);
        TourismTaxRule tax = tourismTaxRuleRepository.findByProperty_Id(property.getId()).orElse(null);
        return new PmsExtensionsResponse(
                settings == null ? new PmsExtensionsResponse.BookingSettingsView(null,
                        property.getCode().toLowerCase(Locale.ROOT), false, false, null, null, null)
                        : new PmsExtensionsResponse.BookingSettingsView(settings.getId(), settings.getPublicSlug(), settings.isEnabled(),
                        settings.isRequireGuarantee(), settings.getTermsUrl(), settings.getPrivacyUrl(),
                        settings.getConfirmationMessage()),
                tax == null ? new PmsExtensionsResponse.TourismTaxRuleView(null, false, "Kurtaxe",
                        BigDecimal.ZERO, BigDecimal.ZERO, 16, null)
                        : new PmsExtensionsResponse.TourismTaxRuleView(tax.getId(), tax.isEnabled(), tax.getName(),
                        tax.getAdultRate(), tax.getChildRate(), tax.getChildFreeUnder(), tax.getMaximumNights()),
                posTicketRepository.findAllByProperty_IdOrderByCreatedAtDesc(property.getId()).stream()
                        .limit(100).map(this::toPosTicket).toList(),
                accessCredentialRepository.findAllByProperty_IdOrderByIssuedAtDesc(property.getId()).stream()
                        .limit(100).map(this::toAccessCredential).toList(),
                migrationBatchRepository.findAllByProperty_IdOrderByCreatedAtDesc(property.getId()).stream()
                        .limit(50).map(this::toMigrationBatch).toList());
    }

    private PmsExtensionsResponse.PosTicketView toPosTicket(PosTicket ticket) {
        String guestName = ticket.getFolio() == null ? null
                : ticket.getFolio().getReservation().getGuest().getFirstName() + " "
                + ticket.getFolio().getReservation().getGuest().getLastName();
        return new PmsExtensionsResponse.PosTicketView(ticket.getId(), ticket.getTicketNumber(),
                ticket.getOutletCode(), ticket.getTableReference(), ticket.getServiceDate(), ticket.getStatus().name(),
                ticket.getPaymentMethod() == null ? "ROOM_CHARGE" : ticket.getPaymentMethod().name(),
                ticket.getFolio() == null ? null : ticket.getFolio().getId(), guestName, ticket.getCurrencyCode(),
                ticket.getNetAmount(), ticket.getTaxAmount(), ticket.getGrossAmount(), ticket.getCreatedAt(),
                ticket.getLines().stream().map(line -> new PmsExtensionsResponse.PosLineView(line.getDescription(),
                        line.getQuantity(), line.getUnitPrice(), line.getTaxRate(), line.getGrossAmount())).toList());
    }

    private PmsExtensionsResponse.AccessCredentialView toAccessCredential(AccessCredential credential) {
        AccessCredentialStatus status = credential.getStatus() == AccessCredentialStatus.ACTIVE
                && credential.getValidUntil().isBefore(LocalDateTime.now())
                ? AccessCredentialStatus.EXPIRED : credential.getStatus();
        Reservation reservation = credential.getReservation();
        return new PmsExtensionsResponse.AccessCredentialView(credential.getId(), reservation.getId(),
                reservation.getConfirmationCode(), reservation.getGuest().getFirstName() + " "
                + reservation.getGuest().getLastName(), credential.getRoom().getNumber(), credential.getProviderCode(),
                credential.getExternalReference(), status.name(), credential.getValidFrom(), credential.getValidUntil(),
                credential.getIssuedAt());
    }

    private PmsExtensionsResponse.MigrationBatchView toMigrationBatch(MigrationBatch batch) {
        return new PmsExtensionsResponse.MigrationBatchView(batch.getId(), batch.getIdempotencyKey(),
                batch.getSourceSystem(), batch.getStatus().name(), batch.getImportedGuests(),
                batch.getImportedReservations(), batch.getImportedPayments(), batch.getTotalOpeningBalance(),
                batch.getReconciliationMessage(), batch.getCreatedAt(), batch.getCompletedAt());
    }

    private HotelProperty requireProperty(Company company, Long propertyId) {
        return propertyRepository.findByIdAndCompany_Id(propertyId, company.getId())
                .orElseThrow(() -> notFound("Hotel nicht gefunden."));
    }

    private HotelProperty requirePublicProperty(String code) {
        return bookingSettingsRepository.findByPublicSlugIgnoreCaseAndEnabledTrue(required(code))
                .map(BookingEngineSettings::getProperty)
                .filter(HotelProperty::isActive)
                .orElseThrow(() -> notFound("Die Onlinebuchung ist für dieses Hotel nicht aktiviert."));
    }

    private Reservation requireReservation(Company company, Long propertyId, Long reservationId) {
        return reservationRepository.findByIdAndProperty_Company_Id(reservationId, company.getId())
                .filter(value -> value.getProperty().getId().equals(propertyId))
                .orElseThrow(() -> notFound("Reservierung nicht gefunden."));
    }

    private String nextTicketNumber(Long propertyId) {
        String value;
        do {
            value = "POS-" + LocalDate.now().toString().replace("-", "") + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (posTicketRepository.existsByProperty_IdAndTicketNumberIgnoreCase(propertyId, value));
        return value;
    }

    private PublicBookingResponse publicBookingResponse(Reservation reservation, BookingEngineSettings settings,
                                                        boolean verificationRequired) {
        return new PublicBookingResponse(reservation.getConfirmationCode(), reservation.getProperty().getName(),
                reservation.getRoomType().getName(), reservation.getRatePlan().getName(),
                reservation.getCurrencyCode(), reservation.getTotalAmount(), clean(settings.getConfirmationMessage()),
                reservation.getStatus().name(), verificationRequired, reservation.getHoldUntil());
    }

    private void validatePublicStay(LocalDate arrival, LocalDate departure) {
        if (arrival == null || departure == null || !departure.isAfter(arrival)) {
            throw badRequest("Abreise muss nach der Anreise liegen.");
        }
        long nights = ChronoUnit.DAYS.between(arrival, departure);
        if (nights > publicMaxStayNights) {
            throw badRequest("Onlinebuchungen sind auf maximal " + publicMaxStayNights + " Nächte begrenzt.");
        }
    }

    private String validateIdempotencyKey(String value) {
        String key = required(value);
        if (!key.matches("^[A-Za-z0-9._:-]{16,80}$")) {
            throw badRequest("Der Idempotency-Key muss 16 bis 80 sichere Zeichen enthalten.");
        }
        return key;
    }

    private String publicBookingFingerprint(PmsExtensionsRequests.PublicBooking request) {
        String canonical = String.join("\n",
                Objects.toString(request.arrivalDate(), ""),
                Objects.toString(request.departureDate(), ""),
                Objects.toString(request.ratePlanId(), ""),
                String.valueOf(request.adults()),
                String.valueOf(request.children()),
                Objects.toString(clean(request.firstName()), ""),
                Objects.toString(clean(request.lastName()), ""),
                Objects.toString(clean(request.email()), "").toLowerCase(Locale.ROOT),
                Objects.toString(clean(request.phone()), ""),
                String.valueOf(request.termsAccepted()),
                String.valueOf(request.privacyAccepted()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private void emit(HotelProperty property, String eventType, String aggregateType, String aggregateId, String payload) {
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setProperty(property);
        event.setEventType(eventType);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private void appendPosting(StringBuilder csv, String date, String document, String debitAccount,
                               String creditAccount, String description, BigDecimal signedAmount,
                               String currency, BigDecimal taxRate) {
        BigDecimal amount = signedAmount.abs().setScale(2, RoundingMode.HALF_UP);
        String effectiveDebit = signedAmount.signum() < 0 ? creditAccount : debitAccount;
        String effectiveCredit = signedAmount.signum() < 0 ? debitAccount : creditAccount;
        csv.append(csv(date)).append(';').append(csv(document)).append(';').append(csv(effectiveDebit)).append(';')
                .append(csv(effectiveCredit)).append(';').append(csv(description)).append(';')
                .append(amount.toPlainString()).append(';').append(amount.toPlainString()).append(';')
                .append(csv(currency)).append(';').append(taxRate.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append('\n');
    }

    private String revenueAccount(FolioItemType type) {
        return switch (type) {
            case ROOM -> "3200";
            case BREAKFAST -> "3210";
            case SERVICE, OTHER -> "3220";
            case TAX -> "3600";
            case DISCOUNT -> "3290";
        };
    }

    private String paymentAccount(PaymentMethod method) {
        return switch (method) {
            case CASH -> "1000";
            case CARD -> "1020";
            case BANK_TRANSFER -> "1021";
            case VOUCHER -> "1090";
            case OTHER -> "1099";
        };
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        int firstContent = 0;
        while (firstContent < safe.length() && Character.isWhitespace(safe.charAt(firstContent))) {
            firstContent++;
        }
        boolean leadingControl = !safe.isEmpty()
                && (safe.charAt(0) == '\t' || safe.charAt(0) == '\r' || safe.charAt(0) == '\n');
        boolean formula = firstContent < safe.length()
                && "=+-@".indexOf(safe.charAt(firstContent)) >= 0;
        if (leadingControl || formula) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private String actor(String value) { return clean(value) == null ? "system" : clean(value); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String required(String value) {
        String result = clean(value);
        if (result == null) throw badRequest("Ein Pflichtfeld fehlt.");
        return result;
    }
    private void validatePublicUrl(String value, String label) {
        if (clean(value) != null && !clean(value).toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw badRequest(label + " muss HTTPS verwenden.");
        }
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
