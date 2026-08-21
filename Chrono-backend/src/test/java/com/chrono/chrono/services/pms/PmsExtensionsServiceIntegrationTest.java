package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsExtensionsRequests;
import com.chrono.chrono.dto.pms.UpsertReservationRequest;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PmsExtensionsService.class, PmsOperationsService.class, PmsAuditWriter.class})
@ActiveProfiles("test")
@RecordApplicationEvents
class PmsExtensionsServiceIntegrationTest {
    @Autowired private PmsExtensionsService service;
    @Autowired private PmsOperationsService operationsService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private HotelPropertyRepository propertyRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private GuestProfileRepository guestRepository;
    @Autowired private RatePlanRepository ratePlanRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private FolioRepository folioRepository;
    @Autowired private FolioItemRepository folioItemRepository;
    @Autowired private PosTicketRepository posTicketRepository;
    @Autowired private AccessCredentialRepository accessCredentialRepository;
    @Autowired private MigrationBatchRepository migrationBatchRepository;
    @Autowired private ApplicationEvents applicationEvents;

    private Company company;
    private HotelProperty property;
    private RoomType roomType;
    private Room room;
    private RatePlan ratePlan;
    private GuestProfile guest;
    private LocalDate arrival;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Chrono Hotel AG"));
        property = new HotelProperty();
        property.setCompany(company);
        property.setCode("ZRH");
        property.setName("Chrono Zürich");
        property.setCurrencyCode("CHF");
        property.setTimezone("Europe/Zurich");
        property = propertyRepository.save(property);
        roomType = new RoomType();
        roomType.setProperty(property);
        roomType.setCode("DBL");
        roomType.setName("Doppelzimmer");
        roomType.setMaxOccupancy(3);
        roomType = roomTypeRepository.save(roomType);
        room = new Room();
        room.setProperty(property);
        room.setRoomType(roomType);
        room.setNumber("101");
        room.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        room = roomRepository.save(room);
        ratePlan = new RatePlan();
        ratePlan.setProperty(property);
        ratePlan.setRoomType(roomType);
        ratePlan.setCode("BAR");
        ratePlan.setName("Beste Rate");
        ratePlan.setCurrencyCode("CHF");
        ratePlan.setNightlyRate(new BigDecimal("100.00"));
        ratePlan.setMinStay(1);
        ratePlan = ratePlanRepository.save(ratePlan);
        guest = new GuestProfile();
        guest.setCompany(company);
        guest.setFirstName("Gabriela");
        guest.setLastName("Tschopp");
        guest.setEmail("gabriela@example.com");
        guest = guestRepository.save(guest);
        arrival = LocalDate.now().plusDays(2);
    }

    @Test
    void exposesARealPublicBookingFlowUsingPmsAvailability() {
        service.updateBookingSettings(company, property.getId(), new PmsExtensionsRequests.BookingSettings(
                "chrono-zuerich", true, true, "https://hotel.example/agb", "https://hotel.example/datenschutz", "Bis bald"));

        var request = new PmsExtensionsRequests.PublicBooking(
                arrival, arrival.plusDays(2), ratePlan.getId(), 2, 0, "Raja", "Siefert",
                "raja@example.com", "+41790000000", true, true);
        var response = service.createPublicBooking(
                "chrono-zuerich", "booking-test-00000001", request);
        var repeated = service.createPublicBooking(
                "chrono-zuerich", "booking-test-00000001", request);

        assertThat(response.confirmationCode()).isNotBlank();
        assertThat(repeated.confirmationCode()).isEqualTo(response.confirmationCode());
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
        assertThat(response.status()).isEqualTo("TENTATIVE");
        assertThat(response.verificationRequired()).isTrue();
        assertThat(reservationRepository.findAll()).singleElement().satisfies(reservation -> {
            assertThat(reservation.getSource()).isEqualTo(ReservationSource.BOOKING_ENGINE);
            assertThat(reservation.getGuaranteeStatus()).isEqualTo(ReservationGuaranteeStatus.DEPOSIT_REQUIRED);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.TENTATIVE);
        });
    }

    @Test
    void confirmsAnUnguaranteedPublicBookingOnlyAfterEmailVerification() {
        service.updateBookingSettings(company, property.getId(), new PmsExtensionsRequests.BookingSettings(
                "chrono-zuerich", true, false, "https://hotel.example/agb",
                "https://hotel.example/datenschutz", "Bis bald"));
        var request = new PmsExtensionsRequests.PublicBooking(
                arrival, arrival.plusDays(2), ratePlan.getId(), 2, 0, "Raja", "Siefert",
                "raja@example.com", null, true, true);

        service.createPublicBooking("chrono-zuerich", "booking-test-verify-001", request);
        String token = applicationEvents.stream(PublicBookingVerificationRequested.class)
                .findFirst().orElseThrow().token();
        var verified = service.verifyPublicBooking("chrono-zuerich", token);

        assertThat(verified.verificationRequired()).isFalse();
        assertThat(verified.status()).isEqualTo("CONFIRMED");
        assertThat(reservationRepository.findAll()).singleElement()
                .extracting(Reservation::getStatus).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void rejectsUnboundedPublicAvailabilityWindows() {
        service.updateBookingSettings(company, property.getId(), new PmsExtensionsRequests.BookingSettings(
                "chrono-zuerich", true, false, "https://hotel.example/agb",
                "https://hotel.example/datenschutz", "Bis bald"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.publicAvailability(
                        "chrono-zuerich", arrival, arrival.plusDays(31)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void postsTourismTaxPosChargesAndProviderNeutralRoomAccess() {
        Reservation reservation = operationsService.createReservationRecord(company,
                new UpsertReservationRequest(property.getId(), guest.getId(), roomType.getId(), room.getId(),
                        ratePlan.getId(), arrival, arrival.plusDays(2), 2, 1, ReservationStatus.CONFIRMED,
                        ReservationSource.DIRECT, null), "Christopher");
        Long folioId = folioRepository.findFirstByReservation_IdOrderByIdAsc(reservation.getId()).orElseThrow().getId();
        service.updateTourismTax(company, property.getId(), new PmsExtensionsRequests.TourismTaxRuleRequest(
                true, "Kurtaxe Zürich", new BigDecimal("3.50"), new BigDecimal("1.50"), 16, 10));
        service.postTourismTax(company, property.getId(),
                new PmsExtensionsRequests.PostTourismTax(reservation.getId(), 1), "Christopher");
        service.createPosTicket(company, property.getId(), new PmsExtensionsRequests.CreatePosTicket(
                folioId, "BAR", "12", arrival, null, List.of(new PmsExtensionsRequests.PosLine(
                "Abendessen", BigDecimal.ONE, new BigDecimal("40.00"), new BigDecimal("8.10")))), "Christopher");
        service.issueAccessCredential(company, property.getId(), new PmsExtensionsRequests.IssueAccessCredential(
                reservation.getId(), "SALTO", "secret-manager:key-4711", LocalDateTime.now(),
                LocalDateTime.now().plusDays(2)), "Christopher");

        assertThat(folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folioId))
                .extracting(FolioItem::getType).contains(FolioItemType.TAX, FolioItemType.SERVICE);
        assertThat(posTicketRepository.findAll()).singleElement()
                .extracting(PosTicket::getGrossAmount).isEqualTo(new BigDecimal("43.24"));
        assertThat(accessCredentialRepository.findAll()).singleElement()
                .extracting(AccessCredential::getStatus).isEqualTo(AccessCredentialStatus.ACTIVE);
        String export = new String(service.accountingExport(company, property.getId(), LocalDate.now(),
                arrival.plusDays(3)), StandardCharsets.UTF_8);
        assertThat(export).contains("debitAccount;creditAccount", "Kurtaxe Zürich", "POS BAR");
        export.lines().skip(1).filter(line -> !line.isBlank()).forEach(line -> {
            String[] fields = line.split(";", -1);
            assertThat(fields[5]).isEqualTo(fields[6]);
        });
    }

    @Test
    void importsMigrationRowsIdempotentlyAndFlagsPriceDifferences() {
        var importRequest = new PmsExtensionsRequests.MigrationImport("batch-001", "LegacyPMS", List.of(
                new PmsExtensionsRequests.MigrationReservation("LEG-1", "Alex", "Meier", "alex@example.com",
                        null, roomType.getId(), ratePlan.getId(), arrival, arrival.plusDays(2), 1, 0,
                        new BigDecimal("210.00"), new BigDecimal("50.00"))));

        service.importMigration(company, property.getId(), importRequest, "Christopher");
        service.importMigration(company, property.getId(), importRequest, "Christopher");

        assertThat(migrationBatchRepository.findAll()).singleElement().satisfies(batch -> {
            assertThat(batch.getStatus()).isEqualTo(MigrationBatchStatus.RECONCILIATION_REQUIRED);
            assertThat(batch.getImportedReservations()).isEqualTo(1);
            assertThat(batch.getImportedPayments()).isEqualTo(1);
            assertThat(batch.getTotalOpeningBalance()).isEqualByComparingTo("160.00");
        });
        assertThat(reservationRepository.findAll()).hasSize(1);
    }
}
