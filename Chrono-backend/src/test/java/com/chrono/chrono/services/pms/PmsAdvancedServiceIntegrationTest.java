package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PmsOperationsService.class, PmsAdvancedService.class, PmsAuditWriter.class,
        PmsDocumentFingerprintService.class})
@ActiveProfiles("test")
class PmsAdvancedServiceIntegrationTest {

    @Autowired
    private PmsAdvancedService service;
    @Autowired
    private PmsOperationsService operationsService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private HotelPropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private GuestProfileRepository guestRepository;
    @Autowired
    private RatePlanRepository ratePlanRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private GroupBookingRepository groupRepository;
    @Autowired
    private FolioItemRepository folioItemRepository;
    @Autowired
    private NightAuditRepository nightAuditRepository;
    @Autowired
    private PmsInvoiceRepository invoiceRepository;
    @Autowired
    private GuestRegistrationRepository guestRegistrationRepository;

    private Company company;
    private HotelProperty property;
    private RoomType roomType;
    private Room room101;
    private Room room102;
    private GuestProfile guest;
    private GuestProfile secondGuest;
    private RatePlan ratePlan;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Chrono Hotel AG"));
        property = new HotelProperty();
        property.setCompany(company);
        property.setCode("ZRH");
        property.setName("Chrono Zürich");
        property.setLegalName("Chrono Hotel AG");
        property.setAddressLine1("Musterstrasse 1");
        property.setPostalCode("8000");
        property.setCity("Zürich");
        property.setTimezone("Europe/Zurich");
        property.setCurrencyCode("CHF");
        property = propertyRepository.save(property);

        roomType = new RoomType();
        roomType.setProperty(property);
        roomType.setCode("DBL");
        roomType.setName("Doppelzimmer");
        roomType.setBaseOccupancy(1);
        roomType.setMaxOccupancy(2);
        roomType = roomTypeRepository.save(roomType);
        room101 = room("101");
        room102 = room("102");
        guest = guest("Gabriela", "Tschopp", "gabriela@example.com");
        secondGuest = guest("Raja", "Siefert", "raja@example.com");

        ratePlan = new RatePlan();
        ratePlan.setProperty(property);
        ratePlan.setRoomType(roomType);
        ratePlan.setCode("BAR");
        ratePlan.setName("Beste verfügbare Rate");
        ratePlan.setCurrencyCode("CHF");
        ratePlan.setNightlyRate(new BigDecimal("108.10"));
        ratePlan.setMinStay(1);
        ratePlan = ratePlanRepository.save(ratePlan);
        today = LocalDate.now(ZoneId.of("Europe/Zurich"));
    }

    @Test
    void createsAtomicGroupWithRoomingListAndFolios() {
        PmsAdvancedResponse response = service.createGroupBooking(
                company,
                groupRequest(List.of(
                        rooming(guest, room101),
                        rooming(secondGuest, room102)
                )),
                "Christopher",
                today
        );

        assertThat(response.groups()).hasSize(1);
        assertThat(response.groups().get(0).rooms()).hasSize(2);
        assertThat(response.groups().get(0).rooms())
                .extracting(PmsAdvancedResponse.GroupMemberView::guestName)
                .containsExactlyInAnyOrder("Gabriela Tschopp", "Raja Siefert");
        assertThat(reservationRepository.findAll()).hasSize(2);
        assertThat(groupRepository.findAll()).singleElement()
                .extracting(GroupBooking::getGroupCode).isEqualTo("TEAM-26");
        assertThat(operationsService.getOperations(company, property.getId(), today, null, null).folios())
                .hasSize(2);
    }

    @Test
    void createsSplitFolioAndMovesOnlySelectedCharges() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();
        PmsOperationsResponse.FolioView mainFolio = created.folios().get(0);
        Long firstItemId = mainFolio.items().get(0).id();

        PmsOperationsResponse split = service.createSplitFolio(
                company, property.getId(),
                new CreateSplitFolioRequest(reservationId, "Firmenkonto", null), today);
        PmsOperationsResponse.FolioView target = split.folios().stream()
                .filter(folio -> "Firmenkonto".equals(folio.label())).findFirst().orElseThrow();

        PmsOperationsResponse moved = service.moveFolioItems(
                company, property.getId(), mainFolio.id(),
                new MoveFolioItemsRequest(target.id(), List.of(firstItemId)), today);

        assertThat(moved.folios()).hasSize(2);
        assertThat(folioItemRepository.findById(firstItemId).orElseThrow().getFolio().getId())
                .isEqualTo(target.id());
    }

    @Test
    void snapshotsInvoiceWithSwissVatAndRendersPdf() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long folioId = created.folios().get(0).id();

        PmsAdvancedResponse response = service.createInvoice(
                company, property.getId(),
                new CreateInvoiceRequest(
                        folioId, today.plusDays(10), new BigDecimal("8.10"),
                        "Gabriela Tschopp", "Seestrasse 2", "8002", "Zürich", "CH",
                        "CH9300762011623852957", null
                ),
                today
        );

        PmsAdvancedResponse.InvoiceView invoice = response.invoices().get(0);
        assertThat(invoice.grossAmount()).isEqualByComparingTo("216.20");
        assertThat(invoice.netAmount()).isEqualByComparingTo("200.00");
        assertThat(invoice.vatAmount()).isEqualByComparingTo("16.20");
        assertThat(invoice.hasQrPaymentPart()).isTrue();
        assertThat(service.generateInvoicePdf(company, invoice.id()))
                .startsWith("%PDF".getBytes());
        String[] qrFields = service.swissQrPayload(invoiceRepository.findById(invoice.id()).orElseThrow())
                .split("\\n", -1);
        assertThat(qrFields).hasSize(34);
        assertThat(qrFields[4]).isEqualTo("S");
        assertThat(qrFields[6]).isEqualTo("Musterstrasse");
        assertThat(qrFields[7]).isEqualTo("1");
        assertThat(qrFields[20]).isEqualTo("S");
        assertThat(qrFields[22]).isEqualTo("Seestrasse");
        assertThat(qrFields[23]).isEqualTo("2");
    }

    @Test
    void correctsIssuedInvoiceWithImmutableNegativeCreditNote() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        PmsAdvancedResponse issued = service.createInvoice(
                company, property.getId(),
                new CreateInvoiceRequest(
                        created.folios().get(0).id(), today.plusDays(10), new BigDecimal("8.10"),
                        "Gabriela Tschopp", "Seestrasse 2", "8002", "Zürich", "CH",
                        null, null
                ),
                today
        );
        Long invoiceId = issued.invoices().get(0).id();

        PmsAdvancedResponse corrected = service.correctInvoice(
                company,
                property.getId(),
                invoiceId,
                new CorrectInvoiceRequest("Adresse war falsch"),
                "Christopher",
                today
        );

        assertThat(corrected.invoices()).hasSize(2);
        PmsAdvancedResponse.InvoiceView credit = corrected.invoices().stream()
                .filter(invoice -> invoice.type() == InvoiceType.CREDIT_NOTE)
                .findFirst()
                .orElseThrow();
        PmsAdvancedResponse.InvoiceView original = corrected.invoices().stream()
                .filter(invoice -> invoice.id().equals(invoiceId))
                .findFirst()
                .orElseThrow();
        assertThat(credit.grossAmount()).isEqualByComparingTo("-216.20");
        assertThat(credit.originalInvoiceId()).isEqualTo(invoiceId);
        assertThat(credit.correctionReason()).isEqualTo("Adresse war falsch");
        assertThat(original.status()).isEqualTo(InvoiceStatus.CREDITED);
        assertThat(service.generateInvoicePdf(company, credit.id())).startsWith("%PDF".getBytes());
        assertThatThrownBy(() -> service.correctInvoice(
                company, property.getId(), invoiceId,
                new CorrectInvoiceRequest("Nochmals"), "Christopher", today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("noch nicht korrigierte");
    }

    @Test
    void closesNightAuditOnlyOnceAndCanMarkNoShows() {
        operationsService.createReservation(company, new UpsertReservationRequest(
                property.getId(), guest.getId(), roomType.getId(), room101.getId(), ratePlan.getId(),
                today, today.plusDays(2), 1, 0, ReservationStatus.CONFIRMED,
                ReservationSource.DIRECT, null), "Christopher", today);

        PmsAdvancedResponse response = service.closeNightAudit(
                company, property.getId(), new CloseNightAuditRequest(today, true), "Christopher");

        assertThat(response.nightAudits()).singleElement()
                .extracting(PmsAdvancedResponse.NightAuditView::noShowCount).isEqualTo(1L);
        assertThat(reservationRepository.findAll().get(0).getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThatThrownBy(() -> service.closeNightAudit(
                company, property.getId(), new CloseNightAuditRequest(today, true), "Christopher"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("bereits abgeschlossen");
        assertThat(nightAuditRepository.findAll()).hasSize(1);
    }

    @Test
    void queuesRenderedGuestCommunicationWithoutPretendingItWasSent() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();
        PmsAdvancedResponse templates = service.createTemplate(
                company, property.getId(),
                new UpsertCommunicationTemplateRequest(
                        "PRE_ARRIVAL", "Vor Anreise", "Willkommen {{guestName}}",
                        "{{hotelName}} erwartet Sie am {{arrivalDate}}. Code: {{confirmationCode}}",
                        "de", true
                ), today);

        PmsAdvancedResponse response = service.queueCommunication(
                company, property.getId(),
                new QueueCommunicationRequest(
                        guest.getId(), reservationId, templates.communicationTemplates().get(0).id(), null),
                today
        );

        assertThat(response.communications()).singleElement().satisfies(communication -> {
            assertThat(communication.status()).isEqualTo(CommunicationStatus.QUEUED);
            assertThat(communication.subject()).isEqualTo("Willkommen Gabriela Tschopp");
            assertThat(communication.body()).contains("Chrono Zürich", "Code:");
            assertThat(communication.sentAt()).isNull();
        });
        assertThat(response.integrationOutbox())
                .extracting(PmsAdvancedResponse.OutboxEventView::eventType)
                .contains("communication.queued");
    }

    @Test
    void recordsReadsAndRepliesToUnifiedInboxMessages() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();

        PmsAdvancedResponse received = service.recordInboundCommunication(
                company, property.getId(),
                new PostInboundCommunicationRequest(
                        guest.getId(), reservationId, CommunicationChannel.OTA,
                        "Gabriela Tschopp", "Anreise", "Können wir früher einchecken?",
                        "booking-thread-4711"
                ),
                today
        );

        PmsAdvancedResponse.GuestCommunicationView inbound = received.communications().get(0);
        assertThat(inbound.direction()).isEqualTo(CommunicationDirection.INBOUND);
        assertThat(inbound.status()).isEqualTo(CommunicationStatus.RECEIVED);
        assertThat(inbound.channel()).isEqualTo(CommunicationChannel.OTA);
        assertThat(inbound.readAt()).isNull();

        PmsAdvancedResponse read = service.markCommunicationRead(
                company, property.getId(), inbound.id(), today);
        assertThat(read.communications().get(0).readAt()).isNotNull();

        PmsAdvancedResponse replied = service.queueInboxReply(
                company, property.getId(),
                new QueueInboxReplyRequest(
                        guest.getId(), reservationId, CommunicationChannel.OTA,
                        "booking-thread-4711", "Re: Anreise",
                        "Ja, ab 13 Uhr ist das Zimmer bereit.", "booking-thread-4711"
                ),
                today
        );

        assertThat(replied.communications().get(0)).satisfies(reply -> {
            assertThat(reply.direction()).isEqualTo(CommunicationDirection.OUTBOUND);
            assertThat(reply.status()).isEqualTo(CommunicationStatus.QUEUED);
            assertThat(reply.channel()).isEqualTo(CommunicationChannel.OTA);
            assertThat(reply.externalThreadId()).isEqualTo("booking-thread-4711");
        });
        assertThat(replied.integrationOutbox())
                .extracting(PmsAdvancedResponse.OutboxEventView::eventType)
                .contains("communication.received", "communication.reply_queued");
    }

    @Test
    void importsExternalBookingIdempotentlyAcrossProviderRetries() {
        ExternalBookingRequest request = new ExternalBookingRequest(
                "CHANNEL_TEST", "external-4711", reservationRequest());

        service.importExternalBooking(company, request, "Christopher", today);
        PmsOperationsResponse retried = service.importExternalBooking(company, request, "Christopher", today);

        assertThat(reservationRepository.findAll()).hasSize(1);
        assertThat(retried.reservations()).hasSize(1);
        assertThat(service.getAdvanced(company, property.getId(), today).integrationOutbox())
                .extracting(PmsAdvancedResponse.OutboxEventView::eventType)
                .contains("booking.imported");
    }

    @Test
    void configuresProviderNeutralSandboxMappingAndPublishesInventorySnapshot() {
        PmsAdvancedResponse created = service.createChannelConnection(
                company,
                property.getId(),
                new CreateChannelConnectionRequest(
                        "booking-test", "Booking Test", ChannelEnvironment.SANDBOX, null,
                        List.of(new CreateChannelConnectionRequest.Mapping(
                                roomType.getId(), ratePlan.getId(), "DBL-EXT", "BAR-EXT"))
                ),
                today
        );
        Long connectionId = created.channelConnections().get(0).id();
        assertThat(created.channelConnections().get(0).mappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.externalRoomCode()).isEqualTo("DBL-EXT");
            assertThat(mapping.externalRateCode()).isEqualTo("BAR-EXT");
        });

        PmsAdvancedResponse synced = service.syncChannelConnection(
                company, property.getId(), connectionId, today);
        assertThat(synced.channelConnections().get(0).lastSyncMessage()).contains("Testabgleich");
        assertThat(synced.integrationOutbox())
                .extracting(PmsAdvancedResponse.OutboxEventView::eventType)
                .contains("channel.inventory_snapshot_ready");

        assertThatThrownBy(() -> service.createChannelConnection(
                company,
                property.getId(),
                new CreateChannelConnectionRequest(
                        "live-test", "Live Test", ChannelEnvironment.LIVE, null,
                        List.of(new CreateChannelConnectionRequest.Mapping(
                                roomType.getId(), ratePlan.getId(), "D", "R"))
                ),
                today
        )).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Server-Variable");

        assertThatThrownBy(() -> service.createChannelConnection(
                company,
                property.getId(),
                new CreateChannelConnectionRequest(
                        "live-raw-secret", "Live Raw Secret", ChannelEnvironment.LIVE, "do-not-store-this",
                        List.of(new CreateChannelConnectionRequest.Mapping(
                                roomType.getId(), ratePlan.getId(), "D2", "R2"))
                ),
                today
        )).isInstanceOf(ResponseStatusException.class).hasMessageContaining("env:NAME");

        PmsAdvancedResponse live = service.createChannelConnection(
                company,
                property.getId(),
                new CreateChannelConnectionRequest(
                        "live-gateway", "Live Gateway", ChannelEnvironment.LIVE, "env:CHANNEL_PROVIDER_SECRET",
                        List.of(new CreateChannelConnectionRequest.Mapping(
                                roomType.getId(), ratePlan.getId(), "D3", "R3"))
                ),
                today
        );
        Long liveConnectionId = live.channelConnections().stream()
                .filter(connection -> connection.environment() == ChannelEnvironment.LIVE)
                .findFirst().orElseThrow().id();

        assertThatThrownBy(() -> service.syncChannelConnection(
                company, property.getId(), liveConnectionId, today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("sichere Anbieteranbindung");

        ReflectionTestUtils.setField(service, "providerGatewayEnabled", true);
        PmsAdvancedResponse liveSynced = service.syncChannelConnection(
                company, property.getId(), liveConnectionId, today);
        assertThat(liveSynced.channelConnections().stream()
                .filter(connection -> connection.id().equals(liveConnectionId))
                .findFirst().orElseThrow().lastSyncMessage())
                .contains("sicheren Übertragung");
    }

    @Test
    void completesGuestRegistrationWithoutPersistingDocumentNumberInPlaintext() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();

        PmsAdvancedResponse response = service.completeGuestRegistration(
                company,
                property.getId(),
                reservationId,
                new CompleteGuestRegistrationRequest(
                        "Seestrasse 2", "8002", "Zürich", "CH", "CH",
                        "X123456789", "ZH 12345", "Gabriela Tschopp", true,
                        null, null
                ),
                "Christopher",
                today
        );

        assertThat(response.guestRegistrations()).singleElement().satisfies(registration -> {
            assertThat(registration.status()).isEqualTo(GuestRegistrationStatus.COMPLETED);
            assertThat(registration.documentLastFour()).isEqualTo("6789");
            assertThat(registration.signatureName()).isEqualTo("Gabriela Tschopp");
        });
        GuestRegistration stored = guestRegistrationRepository.findByReservation_Id(reservationId).orElseThrow();
        assertThat(stored.getDocumentHash()).hasSize(64).doesNotContain("X123456789");
    }

    @Test
    void issuesSingleUseDigitalCheckInLinkAndCompletesRegistration() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();

        GuestRegistrationInviteResponse invite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");

        assertThat(invite.token()).isNotBlank();
        assertThat(invite.portalPath()).isEqualTo("/guest-registration/" + invite.token());
        GuestRegistration pending = guestRegistrationRepository.findByReservation_Id(reservationId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(GuestRegistrationStatus.PENDING);
        assertThat(pending.getTokenHash()).hasSize(64).isNotEqualTo(invite.token());
        assertThat(pending.getInvitedBy()).isEqualTo("Christopher");

        PublicGuestRegistrationResponse publicView = service.getPublicGuestRegistration(invite.token());
        assertThat(publicView.status()).isEqualTo(GuestRegistrationStatus.PENDING);
        assertThat(publicView.ruleCode()).isEqualTo("CH-MELDESCHEIN");
        assertThat(publicView.ruleVersion()).isEqualTo(1);
        assertThat(publicView.requiredFields()).contains("documentNumber", "privacyConsent");

        PublicGuestRegistrationResponse completed = service.completePublicGuestRegistration(
                invite.token(),
                new CompleteGuestRegistrationRequest(
                        "Seestrasse 2", "8002", "Zürich", "CH", "CH",
                        "X123456789", "ZH 12345", "Gabriela Tschopp", true,
                        publicView.ruleCode(), publicView.ruleVersion()
                )
        );

        assertThat(completed.status()).isEqualTo(GuestRegistrationStatus.COMPLETED);
        assertThat(completed.ruleCode()).isEqualTo(publicView.ruleCode());
        assertThat(completed.ruleVersion()).isEqualTo(publicView.ruleVersion());
        GuestRegistration stored = guestRegistrationRepository.findByReservation_Id(reservationId).orElseThrow();
        assertThat(stored.getTokenHash()).isNull();
        assertThat(stored.getDocumentHash()).hasSize(64).doesNotContain("X123456789");
        assertThat(stored.getRuleCode()).isEqualTo(publicView.ruleCode());
        assertThat(stored.getRuleVersion()).isEqualTo(publicView.ruleVersion());
        assertThat(stored.getPrivacyConsentAt()).isNotNull();
        assertThatThrownBy(() -> service.getPublicGuestRegistration(invite.token()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ungültig");
    }

    @Test
    void selectsRegistrationRuleFromHotelCountryAndUsesNeutralGlobalFallback() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();

        GuestRegistrationInviteResponse swissInvite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");
        assertThat(service.getPublicGuestRegistration(swissInvite.token()).ruleCode())
                .isEqualTo("CH-MELDESCHEIN");

        property.setCountryCode("DE");
        propertyRepository.saveAndFlush(property);
        GuestRegistrationInviteResponse germanInvite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");
        assertThat(service.getPublicGuestRegistration(germanInvite.token()).ruleCode())
                .isEqualTo("DE-MELDESCHEIN");

        property.setCountryCode("AT");
        propertyRepository.saveAndFlush(property);
        GuestRegistrationInviteResponse globalInvite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");
        PublicGuestRegistrationResponse globalView =
                service.getPublicGuestRegistration(globalInvite.token());
        assertThat(globalView.ruleCode()).isEqualTo("GLOBAL-REGISTRATION");
        assertThat(globalView.ruleVersion()).isEqualTo(1);
        assertThat(globalView.requiredFields())
                .containsExactlyElementsOf(List.of(
                        "addressLine", "postalCode", "city", "countryCode", "nationalityCode",
                        "documentNumber", "signatureName", "privacyConsent"
                ));
    }

    @Test
    void rejectsPublicCompletionWhenDisplayedRuleVersionDoesNotMatchInvite() {
        PmsOperationsResponse created = operationsService.createReservation(
                company, reservationRequest(), "Christopher", today);
        Long reservationId = created.reservations().get(0).id();
        GuestRegistrationInviteResponse invite = service.issueGuestRegistrationInvite(
                company, property.getId(), reservationId, "Christopher");

        assertThatThrownBy(() -> service.completePublicGuestRegistration(
                invite.token(),
                new CompleteGuestRegistrationRequest(
                        "Seestrasse 2", "8002", "Zürich", "CH", "CH",
                        "X123456789", null, "Gabriela Tschopp", true,
                        "GLOBAL-REGISTRATION", 1
                )
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Regelversion")
                .hasMessageContaining("Formular neu");

        GuestRegistration stored =
                guestRegistrationRepository.findByReservation_Id(reservationId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(GuestRegistrationStatus.PENDING);
        assertThat(stored.getPrivacyConsentAt()).isNull();
    }

    @Test
    void booksHotelResourcesWithoutAllowingTimeConflicts() {
        PmsAdvancedResponse withResource = service.createHotelResource(
                company, property.getId(),
                new UpsertHotelResourceRequest(
                        HotelResourceType.CONFERENCE_ROOM, "CONF-1", "Konferenzraum Zürich",
                        "Erdgeschoss", 20, new BigDecimal("120.00"), "CHF", true
                ),
                today
        );
        Long resourceId = withResource.hotelResources().get(0).id();
        LocalDateTime start = today.atTime(9, 0);
        LocalDateTime end = today.atTime(12, 0);

        PmsAdvancedResponse booked = service.createResourceBooking(
                company, property.getId(),
                new CreateResourceBookingRequest(
                        resourceId, null, "Strategiemeeting", "Beispiel AG",
                        start, end, 12, ResourceBookingStatus.CONFIRMED,
                        new BigDecimal("360.00"), "Beamer benötigt"
                ),
                "Christopher", today
        );

        assertThat(booked.resourceBookings()).singleElement().satisfies(booking -> {
            assertThat(booking.resourceName()).isEqualTo("Konferenzraum Zürich");
            assertThat(booking.status()).isEqualTo(ResourceBookingStatus.CONFIRMED);
            assertThat(booking.totalAmount()).isEqualByComparingTo("360.00");
        });
        assertThatThrownBy(() -> service.createResourceBooking(
                company, property.getId(),
                new CreateResourceBookingRequest(
                        resourceId, null, "Überschneidung", "Andere Firma",
                        start.plusHours(1), end.plusHours(1), 4, ResourceBookingStatus.CONFIRMED,
                        BigDecimal.ZERO, null
                ),
                "Christopher", today
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("bereits gebucht");

        Long bookingId = booked.resourceBookings().get(0).id();
        PmsAdvancedResponse cancelled = service.cancelResourceBooking(
                company, property.getId(), bookingId, today);
        assertThat(cancelled.resourceBookings().get(0).status())
                .isEqualTo(ResourceBookingStatus.CANCELLED);
    }

    private Room room(String number) {
        Room value = new Room();
        value.setProperty(property);
        value.setRoomType(roomType);
        value.setNumber(number);
        value.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        return roomRepository.save(value);
    }

    private GuestProfile guest(String firstName, String lastName, String email) {
        GuestProfile value = new GuestProfile();
        value.setCompany(company);
        value.setFirstName(firstName);
        value.setLastName(lastName);
        value.setEmail(email);
        return guestRepository.save(value);
    }

    private CreateGroupBookingRequest groupRequest(List<CreateGroupBookingRequest.RoomingEntry> rooms) {
        return new CreateGroupBookingRequest(
                property.getId(), guest.getId(), null, "TEAM-26", "Team Zürich",
                today.plusDays(1), today.plusDays(3), GroupBookingStatus.CONFIRMED, null, rooms);
    }

    private CreateGroupBookingRequest.RoomingEntry rooming(GuestProfile roomGuest, Room room) {
        return new CreateGroupBookingRequest.RoomingEntry(
                roomGuest.getId(), roomType.getId(), room.getId(), ratePlan.getId(),
                1, 0, ReservationSource.DIRECT, null);
    }

    private UpsertReservationRequest reservationRequest() {
        return new UpsertReservationRequest(
                property.getId(), guest.getId(), roomType.getId(), room101.getId(), ratePlan.getId(),
                today.plusDays(1), today.plusDays(3), 1, 0, ReservationStatus.CONFIRMED,
                ReservationSource.DIRECT, null);
    }
}
