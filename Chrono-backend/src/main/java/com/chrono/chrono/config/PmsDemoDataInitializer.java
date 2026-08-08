package com.chrono.chrono.config;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.services.pms.PmsAdvancedService;
import com.chrono.chrono.services.pms.PmsOperationsService;
import com.chrono.chrono.services.pms.PmsSetupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Creates a broad, idempotent PMS demo scenario for the local Christopher account.
 * It is impossible to activate this component outside the local Spring profile.
 */
@Component
@Profile("local")
@Order(200)
@ConditionalOnProperty(name = "app.pms.demo-data.enabled", havingValue = "true")
public class PmsDemoDataInitializer implements CommandLineRunner {
    static final String DEMO_PROPERTY_CODE = "DEMO";
    private static final Logger log = LoggerFactory.getLogger(PmsDemoDataInitializer.class);
    private static final String ACTOR = "Christopher (Demo-Daten)";

    private final UserRepository userRepository;
    private final HotelPropertyRepository propertyRepository;
    private final PmsSetupService setupService;
    private final PmsOperationsService operationsService;
    private final PmsAdvancedService advancedService;
    private final String username;

    public PmsDemoDataInitializer(
            UserRepository userRepository,
            HotelPropertyRepository propertyRepository,
            PmsSetupService setupService,
            PmsOperationsService operationsService,
            PmsAdvancedService advancedService,
            @Value("${app.pms.test-account.username:Christopher}") String username) {
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.setupService = setupService;
        this.operationsService = operationsService;
        this.advancedService = advancedService;
        this.username = username;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var user = userRepository.findByUsername(username == null ? "" : username.trim())
                .orElse(null);
        if (user == null || user.getCompany() == null) {
            log.info("PMS-Demodaten übersprungen: lokales Testkonto oder Firma fehlt.");
            return;
        }
        Company company = user.getCompany();
        if (propertyRepository.existsByCompany_IdAndCodeIgnoreCase(
                company.getId(), DEMO_PROPERTY_CODE)) {
            log.info("PMS-Demodaten sind bereits vorhanden.");
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Zurich"));
        seed(company, today);
        log.info("PMS-Demodaten für '{}' wurden vollständig angelegt.", username);
    }

    private void seed(Company company, LocalDate today) {
        PmsSetupResponse setup = setupService.createProperty(company, new UpsertHotelPropertyRequest(
                DEMO_PROPERTY_CODE,
                "Chrono Demo Hotel Zürich",
                "Chrono Demo Hotel AG",
                "CH",
                "CHF",
                "Europe/Zurich",
                "Limmatquai 42",
                "8001",
                "Zürich",
                "+41 44 555 01 01",
                "rezeption@demo-hotel.local",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0),
                true));
        PmsSetupResponse.PropertyView property = property(setup);

        setup = setupService.createRoomType(company, property.id(), new UpsertRoomTypeRequest(
                "SGL", "City Einzelzimmer", "Ruhiges Einzelzimmer für Geschäftsreisende.",
                1, 1, 1, "Einzelbett", 10, true));
        Long singleTypeId = roomType(setup, "SGL").id();
        setup = setupService.createRoomType(company, property.id(), new UpsertRoomTypeRequest(
                "DBL", "Classic Doppelzimmer", "Doppelzimmer mit Blick auf die Zürcher Altstadt.",
                2, 3, 1, "Kingsize-Doppelbett", 20, true));
        Long doubleTypeId = roomType(setup, "DBL").id();
        setup = setupService.createRoomType(company, property.id(), new UpsertRoomTypeRequest(
                "STE", "Limmat Suite", "Suite mit Wohnbereich und Platz für Familien.",
                2, 4, 2, "Kingsize-Bett und Schlafsofa", 30, true));
        Long suiteTypeId = roomType(setup, "STE").id();

        setup = createRoom(company, setup, property.id(), singleTypeId, "101", "1", "Nord");
        setup = createRoom(company, setup, property.id(), singleTypeId, "102", "1", "Nord");
        setup = createRoom(company, setup, property.id(), doubleTypeId, "201", "2", "Süd");
        setup = createRoom(company, setup, property.id(), doubleTypeId, "202", "2", "Süd");
        setup = createRoom(company, setup, property.id(), doubleTypeId, "203", "2", "Süd");
        setup = createRoom(company, setup, property.id(), suiteTypeId, "301", "3", "Suiten");
        setup = createRoom(company, setup, property.id(), suiteTypeId, "302", "3", "Suiten");

        Long propertyId = property.id();
        PmsOperationsResponse operations = createGuest(
                company, propertyId, today, "Gabriela", "Tschopp",
                "gabriela.tschopp@example.test", "+41 79 555 10 01",
                today.minusYears(38), "CH", "de", "VIP · ruhiges Zimmer bevorzugt", true);
        operations = createGuest(
                company, propertyId, today, "Daniel", "Meier",
                "daniel.meier@example.test", "+41 79 555 10 02",
                today.minusYears(44), "CH", "de", "Geschäftsreise · frühe Anreise angefragt", false);
        operations = createGuest(
                company, propertyId, today, "Sophie", "Keller",
                "sophie.keller@example.test", "+41 79 555 10 03",
                today.minusYears(31), "CH", "de", "Vegetarisches Frühstück", false);
        operations = createGuest(
                company, propertyId, today, "Lukas", "Frei",
                "lukas.frei@example.test", "+41 79 555 10 04",
                today.minusYears(35), "CH", "de", "Warteliste Firmenreise", false);
        operations = createGuest(
                company, propertyId, today, "Emma", "Baumann",
                "emma.baumann@example.test", "+41 79 555 10 05",
                today.minusYears(29), "DE", "de", "Buchungsoption bis morgen", false);
        operations = createGuest(
                company, propertyId, today, "Noah", "Schmid",
                "noah.schmid@example.test", "+41 79 555 10 06",
                today.minusYears(41), "CH", "de", "Buchung über Demo-Channel", false);
        operations = createGuest(
                company, propertyId, today, "Mia", "Rossi",
                "mia.rossi@example.test", "+41 79 555 10 07",
                today.minusYears(33), "IT", "it", "Teilnehmerin Gruppenreise", false);
        operations = createGuest(
                company, propertyId, today, "Jonas", "Wenger",
                "jonas.wenger@example.test", "+41 79 555 10 08",
                today.minusYears(36), "CH", "de", "Teilnehmer Gruppenreise", false);

        long gabrielaId = guest(operations, "gabriela.tschopp@example.test").id();
        long danielId = guest(operations, "daniel.meier@example.test").id();
        long sophieId = guest(operations, "sophie.keller@example.test").id();
        long lukasId = guest(operations, "lukas.frei@example.test").id();
        long emmaId = guest(operations, "emma.baumann@example.test").id();
        long noahId = guest(operations, "noah.schmid@example.test").id();
        long miaId = guest(operations, "mia.rossi@example.test").id();
        long jonasId = guest(operations, "jonas.wenger@example.test").id();

        operations = operationsService.createRatePlan(company, propertyId, new UpsertRatePlanRequest(
                singleTypeId, "BAR-SGL", "Beste flexible Rate Einzelzimmer",
                new BigDecimal("159.00"), 1, true, true, true), today);
        operations = operationsService.createRatePlan(company, propertyId, new UpsertRatePlanRequest(
                doubleTypeId, "BAR-DBL", "Beste flexible Rate Doppelzimmer",
                new BigDecimal("229.00"), 1, true, true, true), today);
        operations = operationsService.createRatePlan(company, propertyId, new UpsertRatePlanRequest(
                suiteTypeId, "BAR-STE", "Beste flexible Rate Suite",
                new BigDecimal("389.00"), 2, true, true, true), today);
        long singleRateId = rate(operations, "BAR-SGL").id();
        long doubleRateId = rate(operations, "BAR-DBL").id();
        long suiteRateId = rate(operations, "BAR-STE").id();

        operations = operationsService.upsertRateOverride(
                company, propertyId, doubleRateId,
                new UpsertRateOverrideRequest(
                        today.plusDays(5), new BigDecimal("269.00"), 2,
                        false, false, false),
                today);

        long room101 = room(operations, "101").id();
        long room102 = room(operations, "102").id();
        long room201 = room(operations, "201").id();
        long room202 = room(operations, "202").id();
        long room203 = room(operations, "203").id();
        long room301 = room(operations, "301").id();

        operations = createReservation(
                company, today, propertyId, gabrielaId, singleTypeId, room101, singleRateId,
                today.minusDays(1), today.plusDays(2), 1, 0,
                ReservationStatus.CONFIRMED, ReservationSource.DIRECT,
                ReservationGuaranteeStatus.DEPOSIT_PAID, null,
                "VIP-Aufenthalt · Late Check-out angefragt");
        long gabrielaReservationId = reservationForGuest(operations, gabrielaId).id();
        operations = operationsService.checkIn(
                company, gabrielaReservationId, ACTOR, today);

        operations = createReservation(
                company, today, propertyId, danielId, singleTypeId, room102, singleRateId,
                today, today.plusDays(1), 1, 0,
                ReservationStatus.CONFIRMED, ReservationSource.EMAIL,
                ReservationGuaranteeStatus.COMPANY_GUARANTEE, null,
                "Anreise heute · Firmenkostenübernahme liegt vor");
        long danielReservationId = reservationForGuest(operations, danielId).id();

        operations = createReservation(
                company, today, propertyId, sophieId, doubleTypeId, room201, doubleRateId,
                today.minusDays(2), today.plusDays(1), 2, 0,
                ReservationStatus.CONFIRMED, ReservationSource.PHONE,
                ReservationGuaranteeStatus.CREDIT_CARD, null,
                "In-house · Abreise morgen · vegetarisches Frühstück");
        long sophieReservationId = reservationForGuest(operations, sophieId).id();
        operations = operationsService.checkIn(
                company, sophieReservationId, ACTOR, today);

        operations = createReservation(
                company, today, propertyId, lukasId, doubleTypeId, null, doubleRateId,
                today.plusDays(2), today.plusDays(4), 1, 0,
                ReservationStatus.WAITLISTED, ReservationSource.PHONE,
                ReservationGuaranteeStatus.UNGUARANTEED, null,
                "Warteliste · bei Verfügbarkeit sofort bestätigen");

        operations = createReservation(
                company, today, propertyId, emmaId, suiteTypeId, null, suiteRateId,
                today.plusDays(7), today.plusDays(10), 2, 1,
                ReservationStatus.TENTATIVE, ReservationSource.EMAIL,
                ReservationGuaranteeStatus.DEPOSIT_REQUIRED,
                LocalDateTime.now().plusDays(1),
                "Option für Familienwochenende");

        operations = createReservation(
                company, today, propertyId, noahId, doubleTypeId, null, doubleRateId,
                today.minusDays(1), today.plusDays(1), 1, 0,
                ReservationStatus.CONFIRMED, ReservationSource.CHANNEL_MANAGER,
                ReservationGuaranteeStatus.OTA_GUARANTEE, null,
                "Demo No-show vom Channel Manager");
        long noahNoShowReservationId = reservationForGuest(operations, noahId).id();
        operations = operationsService.markNoShow(
                company, noahNoShowReservationId, ACTOR, today);

        operations = operationsService.openCashShift(
                company, propertyId,
                new OpenCashShiftRequest(new BigDecimal("500.00"), "Frühschicht Demo-Rezeption"),
                ACTOR, today);

        PmsOperationsResponse.FolioView gabrielaFolio =
                folioForReservation(operations, gabrielaReservationId);
        operations = operationsService.postFolioItem(
                company, propertyId, gabrielaFolio.id(),
                new PostFolioItemRequest(
                        today, FolioItemType.SERVICE, "Minibar und Mineralwasser",
                        BigDecimal.ONE, new BigDecimal("18.50")),
                today);
        operations = operationsService.postPayment(
                company, propertyId, gabrielaFolio.id(),
                new PostPaymentRequest(
                        new BigDecimal("200.00"), PaymentMethod.CARD, "DEMO-VISA-4242"),
                ACTOR, today);
        long cardPaymentId = folio(operations, gabrielaFolio.id()).paymentEntries().stream()
                .filter(payment -> payment.kind() == PaymentKind.PAYMENT
                        && payment.method() == PaymentMethod.CARD)
                .findFirst()
                .orElseThrow()
                .id();
        operations = operationsService.refundPayment(
                company, propertyId, cardPaymentId,
                new RefundPaymentRequest(
                        new BigDecimal("20.00"), "Korrektur der angezahlten Leistung"),
                ACTOR, today);
        operations = operationsService.postPayment(
                company, propertyId, gabrielaFolio.id(),
                new PostPaymentRequest(
                        new BigDecimal("50.00"), PaymentMethod.CASH, "DEMO-BAR"),
                ACTOR, today);

        operations = advancedService.createHousekeepingTask(
                company, propertyId,
                new CreateHousekeepingTaskRequest(
                        room102, today, HousekeepingTaskType.ARRIVAL, 90, 35,
                        "Priorisierte Anreisevorbereitung", "Lea"),
                today);
        operations = operationsService.createMaintenanceWorkOrder(
                company, propertyId,
                new CreateMaintenanceWorkOrderRequest(
                        room301, "Klimaanlage prüfen",
                        "Die Klimaanlage kühlt unregelmäßig. Technische Prüfung im Demobetrieb.",
                        MaintenancePriority.HIGH, "Technik-Team", today.plusDays(1),
                        true, RoomBlockType.OUT_OF_ORDER, today, today.plusDays(2)),
                ACTOR, today);

        PmsAdvancedResponse advanced = advancedService.createOrganization(
                company, propertyId,
                new UpsertOrganizationRequest(
                        OrganizationType.COMPANY, "Helvetia Consulting AG", "CHE-123.456.789",
                        "Bahnhofstrasse 88", "8001", "Zürich", "CH",
                        "travel@helvetia-consulting.example", "+41 44 555 22 11",
                        "rechnung@helvetia-consulting.example", 30,
                        "Rahmenkunde · Rechnung an Zentrale", true),
                today);
        long organizationId = advanced.organizations().stream()
                .filter(entry -> entry.name().equals("Helvetia Consulting AG"))
                .findFirst()
                .orElseThrow()
                .id();

        advanced = advancedService.createGroupBooking(
                company,
                new CreateGroupBookingRequest(
                        propertyId, miaId, organizationId,
                        "HC-DEMO", "Helvetia Strategie-Retreat",
                        today.plusDays(14), today.plusDays(16),
                        GroupBookingStatus.CONFIRMED,
                        "Zwei Zimmer und Meetingraum für das Strategieteam",
                        List.of(
                                new CreateGroupBookingRequest.RoomingEntry(
                                        miaId, doubleTypeId, room202, doubleRateId,
                                        1, 0, ReservationSource.EMAIL, "Kontaktperson"),
                                new CreateGroupBookingRequest.RoomingEntry(
                                        jonasId, doubleTypeId, room203, doubleRateId,
                                        1, 0, ReservationSource.EMAIL, "Teilnehmer"))),
                ACTOR, today);
        long groupId = advanced.groups().stream()
                .filter(entry -> entry.groupCode().equals("HC-DEMO"))
                .findFirst()
                .orElseThrow()
                .id();

        advanced = advancedService.createHotelResource(
                company, propertyId,
                new UpsertHotelResourceRequest(
                        HotelResourceType.CONFERENCE_ROOM, "SALON-LIMMAT",
                        "Salon Limmat", "Erdgeschoss", 18,
                        new BigDecimal("140.00"), "CHF", true),
                today);
        long resourceId = advanced.hotelResources().stream()
                .filter(entry -> entry.code().equals("SALON-LIMMAT"))
                .findFirst()
                .orElseThrow()
                .id();
        advancedService.createResourceBooking(
                company, propertyId,
                new CreateResourceBookingRequest(
                        resourceId, groupId, "Strategie-Workshop",
                        "Helvetia Consulting AG",
                        today.plusDays(14).atTime(9, 0),
                        today.plusDays(14).atTime(17, 0),
                        8, ResourceBookingStatus.CONFIRMED,
                        new BigDecimal("1120.00"),
                        "U-Form, Bildschirm und Kaffeepausen"),
                ACTOR, today);

        advanced = advancedService.createTemplate(
                company, propertyId,
                new UpsertCommunicationTemplateRequest(
                        "ARRIVAL-DEMO", "Anreiseinformation Demo",
                        "Deine Anreise im {{hotelName}}",
                        "Hallo {{guestName}}, wir freuen uns auf deine Anreise am {{arrivalDate}}. "
                                + "Deine Bestätigung lautet {{confirmationCode}}.",
                        "de", true),
                today);
        long templateId = advanced.communicationTemplates().stream()
                .filter(entry -> entry.code().equals("ARRIVAL-DEMO"))
                .findFirst()
                .orElseThrow()
                .id();
        advancedService.queueCommunication(
                company, propertyId,
                new QueueCommunicationRequest(
                        danielId, danielReservationId, templateId,
                        "daniel.meier@example.test"),
                today);
        advancedService.recordInboundCommunication(
                company, propertyId,
                new PostInboundCommunicationRequest(
                        danielId, danielReservationId, CommunicationChannel.EMAIL,
                        "daniel.meier@example.test", "Re: Anreise",
                        "Vielen Dank. Ich werde voraussichtlich gegen 16:30 Uhr eintreffen.",
                        "demo-thread-daniel"),
                today);

        advanced = advancedService.createChannelConnection(
                company, propertyId,
                new CreateChannelConnectionRequest(
                        "BOOKING_DEMO", "Booking Demo Sandbox", ChannelEnvironment.SANDBOX, null,
                        List.of(new CreateChannelConnectionRequest.Mapping(
                                doubleTypeId, doubleRateId, "DBL-DEMO", "BAR-DEMO"))),
                today);
        long channelId = advanced.channelConnections().stream()
                .filter(entry -> entry.providerCode().equals("BOOKING_DEMO"))
                .findFirst()
                .orElseThrow()
                .id();
        advancedService.syncChannelConnection(company, propertyId, channelId, today);

        operations = advancedService.importExternalBooking(
                company,
                new ExternalBookingRequest(
                        "BOOKING_DEMO", "DEMO-EXT-4711",
                        new UpsertReservationRequest(
                                propertyId, noahId, doubleTypeId, null, doubleRateId,
                                today.plusDays(25), today.plusDays(28), 2, 0,
                                ReservationStatus.CONFIRMED, ReservationSource.CHANNEL_MANAGER,
                                "Importierte Demo-Buchung", ReservationGuaranteeStatus.OTA_GUARANTEE, null)),
                "channel:BOOKING_DEMO", today);

        PmsOperationsResponse.FolioView invoiceFolio =
                folioForReservation(operations, reservationForGuest(operations, emmaId).id());
        advancedService.createInvoice(
                company, propertyId,
                new CreateInvoiceRequest(
                        invoiceFolio.id(), today.plusDays(30), new BigDecimal("8.10"),
                        "Emma Baumann", "Musterweg 7", "8004", "Zürich", "CH",
                        "CH9300762011623852957", null),
                today);

        advancedService.issueGuestRegistrationInvite(
                company, propertyId, danielReservationId, ACTOR);
    }

    private PmsSetupResponse createRoom(
            Company company,
            PmsSetupResponse setup,
            Long propertyId,
            Long roomTypeId,
            String number,
            String floor,
            String section) {
        return setupService.createRoom(company, propertyId, new UpsertRoomRequest(
                roomTypeId, number, "Zimmer " + number, floor, section,
                RoomOperationalStatus.IN_SERVICE, true));
    }

    private PmsOperationsResponse createGuest(
            Company company,
            Long propertyId,
            LocalDate today,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthDate,
            String nationality,
            String language,
            String notes,
            boolean vip) {
        return operationsService.createGuest(
                company, propertyId,
                new UpsertGuestRequest(
                        firstName, lastName, email, phone, birthDate,
                        nationality, language, notes, vip),
                today);
    }

    private PmsOperationsResponse createReservation(
            Company company,
            LocalDate today,
            Long propertyId,
            Long guestId,
            Long roomTypeId,
            Long roomId,
            Long ratePlanId,
            LocalDate arrival,
            LocalDate departure,
            int adults,
            int children,
            ReservationStatus status,
            ReservationSource source,
            ReservationGuaranteeStatus guaranteeStatus,
            LocalDateTime holdUntil,
            String notes) {
        return operationsService.createReservation(
                company,
                new UpsertReservationRequest(
                        propertyId, guestId, roomTypeId, roomId, ratePlanId,
                        arrival, departure, adults, children, status, source,
                        notes, guaranteeStatus, holdUntil),
                ACTOR,
                today);
    }

    private PmsSetupResponse.PropertyView property(PmsSetupResponse response) {
        return response.properties().stream()
                .filter(entry -> entry.code().equalsIgnoreCase(DEMO_PROPERTY_CODE))
                .findFirst()
                .orElseThrow();
    }

    private PmsSetupResponse.RoomTypeView roomType(PmsSetupResponse response, String code) {
        return property(response).roomTypes().stream()
                .filter(entry -> entry.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow();
    }

    private PmsOperationsResponse.GuestView guest(PmsOperationsResponse response, String email) {
        return response.guests().stream()
                .filter(entry -> email.equalsIgnoreCase(entry.email()))
                .findFirst()
                .orElseThrow();
    }

    private PmsOperationsResponse.RatePlanView rate(PmsOperationsResponse response, String code) {
        return response.ratePlans().stream()
                .filter(entry -> entry.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow();
    }

    private PmsOperationsResponse.RoomStateView room(PmsOperationsResponse response, String number) {
        return response.rooms().stream()
                .filter(entry -> entry.number().equalsIgnoreCase(number))
                .findFirst()
                .orElseThrow();
    }

    private PmsOperationsResponse.ReservationView reservationForGuest(
            PmsOperationsResponse response, Long guestId) {
        return response.reservations().stream()
                .filter(entry -> entry.guestId().equals(guestId))
                .findFirst()
                .orElseThrow();
    }

    private PmsOperationsResponse.FolioView folioForReservation(
            PmsOperationsResponse response, Long reservationId) {
        return response.folios().stream()
                .filter(entry -> entry.reservationId().equals(reservationId))
                .findFirst()
                .orElseThrow();
    }

    private PmsOperationsResponse.FolioView folio(
            PmsOperationsResponse response, Long folioId) {
        return response.folios().stream()
                .filter(entry -> entry.id().equals(folioId))
                .findFirst()
                .orElseThrow();
    }
}
