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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PmsOperationsService.class, PmsAuditWriter.class})
@ActiveProfiles("test")
class PmsOperationsServiceIntegrationTest {

    @Autowired
    private PmsOperationsService service;
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
    private FolioRepository folioRepository;
    @Autowired
    private HousekeepingTaskRepository housekeepingTaskRepository;
    @Autowired
    private ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private CashShiftRepository cashShiftRepository;
    @Autowired
    private RoomBlockRepository roomBlockRepository;
    @Autowired
    private MaintenanceWorkOrderRepository maintenanceWorkOrderRepository;
    @Autowired
    private PmsAuditEventRepository auditEventRepository;
    @Autowired
    private PmsAuditWriter auditWriter;

    private Company company;
    private HotelProperty property;
    private RoomType roomType;
    private Room room;
    private GuestProfile guest;
    private RatePlan ratePlan;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Chrono Hotel AG"));
        property = new HotelProperty();
        property.setCompany(company);
        property.setCode("ZRH");
        property.setName("Chrono Zürich");
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

        room = new Room();
        room.setProperty(property);
        room.setRoomType(roomType);
        room.setNumber("101");
        room.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        room = roomRepository.save(room);

        guest = new GuestProfile();
        guest.setCompany(company);
        guest.setFirstName("Gabriela");
        guest.setLastName("Tschopp");
        guest.setEmail("gabriela@example.com");
        guest = guestRepository.save(guest);

        ratePlan = new RatePlan();
        ratePlan.setProperty(property);
        ratePlan.setRoomType(roomType);
        ratePlan.setCode("BAR");
        ratePlan.setName("Beste verfügbare Rate");
        ratePlan.setCurrencyCode("CHF");
        ratePlan.setNightlyRate(new BigDecimal("120.00"));
        ratePlan.setMinStay(1);
        ratePlan = ratePlanRepository.save(ratePlan);

        today = LocalDate.now(ZoneId.of("Europe/Zurich"));
    }

    @Test
    void createsReservationFolioAndReducesAvailability() {
        var response = service.createReservation(
                company,
                reservationRequest(today.plusDays(1), today.plusDays(3), room.getId()),
                "Christopher",
                today
        );

        assertThat(response.reservations()).hasSize(1);
        assertThat(response.reservations().get(0).totalAmount()).isEqualByComparingTo("240.00");
        assertThat(response.folios()).hasSize(1);
        assertThat(response.folios().get(0).items()).hasSize(2);
        assertThat(response.folios().get(0).balance()).isEqualByComparingTo("240.00");

        AvailabilityResponse availability = service.getAvailability(
                company,
                property.getId(),
                today.plusDays(1),
                today.plusDays(3)
        );
        assertThat(availability.roomTypes().get(0).availableRooms()).isZero();
        assertThat(availability.roomTypes().get(0).rates().get(0).available()).isFalse();
        PmsAuditEvent auditEvent = auditEventRepository
                .findTop100ByProperty_IdOrderByCreatedAtDesc(property.getId()).get(0);
        assertThat(auditEvent.getEventType()).isEqualTo("reservation.created");
        assertThat(auditWriter.hasValidIntegrityHash(auditEvent)).isTrue();
    }

    @Test
    void preventsOverbookingTheLastPhysicalRoom() {
        service.createReservation(
                company,
                reservationRequest(today.plusDays(2), today.plusDays(4), null),
                "Christopher",
                today
        );

        assertThatThrownBy(() -> service.createReservation(
                company,
                reservationRequest(today.plusDays(3), today.plusDays(5), null),
                "Christopher",
                today
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ausgebucht");
    }

    @Test
    void appliesDailyRateOverrideAndMinimumStayRestriction() {
        service.upsertRateOverride(
                company,
                property.getId(),
                ratePlan.getId(),
                new UpsertRateOverrideRequest(
                        today.plusDays(1),
                        new BigDecimal("180.00"),
                        2,
                        false,
                        false,
                        false
                ),
                today
        );

        AvailabilityResponse oneNight = service.getAvailability(
                company,
                property.getId(),
                today.plusDays(1),
                today.plusDays(2)
        );
        assertThat(oneNight.roomTypes().get(0).rates().get(0).available()).isFalse();
        assertThat(oneNight.roomTypes().get(0).rates().get(0).restriction())
                .contains("Mindestaufenthalt");

        AvailabilityResponse twoNights = service.getAvailability(
                company,
                property.getId(),
                today.plusDays(1),
                today.plusDays(3)
        );
        assertThat(twoNights.roomTypes().get(0).rates().get(0).available()).isTrue();
        assertThat(twoNights.roomTypes().get(0).rates().get(0).totalAmount())
                .isEqualByComparingTo("300.00");
    }

    @Test
    void requiresBalancedFolioBeforeCheckoutAndCreatesHousekeepingWork() {
        PmsOperationsResponse created = service.createReservation(
                company,
                reservationRequest(today, today.plusDays(1), room.getId()),
                "Christopher",
                today
        );
        Long reservationId = created.reservations().get(0).id();
        Long folioId = created.folios().get(0).id();

        service.checkIn(company, reservationId, today);

        assertThatThrownBy(() -> service.checkOut(company, reservationId, today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ausgeglichen");

        service.postPayment(
                company,
                property.getId(),
                folioId,
                new PostPaymentRequest(new BigDecimal("120.00"), PaymentMethod.BANK_TRANSFER, "bank-test"),
                "Christopher",
                today
        );
        PmsOperationsResponse checkedOut = service.checkOut(company, reservationId, today);

        assertThat(checkedOut.reservations().get(0).status()).isEqualTo(ReservationStatus.CHECKED_OUT);
        assertThat(folioRepository.findById(folioId).orElseThrow().getStatus()).isEqualTo(FolioStatus.CLOSED);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getHousekeepingStatus())
                .isEqualTo(HousekeepingStatus.DIRTY);
        assertThat(housekeepingTaskRepository.findByRoom_IdAndServiceDate(room.getId(), today)).isPresent();
    }

    @Test
    void refusesToPostCardPaymentWithoutConfiguredProvider() {
        PmsOperationsResponse created = service.createReservation(
                company,
                reservationRequest(today, today.plusDays(1), room.getId()),
                "Christopher",
                today
        );
        Long folioId = created.folios().get(0).id();

        assertThatThrownBy(() -> service.postPayment(
                company,
                property.getId(),
                folioId,
                new PostPaymentRequest(new BigDecimal("120.00"), PaymentMethod.CARD, "unverified-card"),
                "Christopher",
                today
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Zahlungsprovider");
    }

    @Test
    void keepsGuestsAndReservationsCompanyScoped() {
        Company otherCompany = companyRepository.save(new Company("Other Hotels AG"));

        assertThatThrownBy(() -> service.createReservation(
                otherCompany,
                reservationRequest(today.plusDays(1), today.plusDays(2), room.getId()),
                "outsider",
                today
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Hotel nicht gefunden");
    }

    @Test
    void waitlistDoesNotConsumeInventoryAndConfirmationRechecksCapacity() {
        service.createReservation(
                company,
                reservationRequest(today.plusDays(2), today.plusDays(4), room.getId()),
                "Christopher",
                today
        );
        UpsertReservationRequest waitlistRequest = new UpsertReservationRequest(
                property.getId(), guest.getId(), roomType.getId(), room.getId(), ratePlan.getId(),
                today.plusDays(2), today.plusDays(4), 1, 0,
                ReservationStatus.WAITLISTED, ReservationSource.PHONE, "Bitte benachrichtigen",
                ReservationGuaranteeStatus.UNGUARANTEED, null
        );
        PmsOperationsResponse response = service.createReservation(
                company, waitlistRequest, "Christopher", today
        );
        PmsOperationsResponse.ReservationView waitlisted = response.reservations().stream()
                .filter(entry -> entry.status() == ReservationStatus.WAITLISTED)
                .findFirst()
                .orElseThrow();

        assertThat(service.getAvailability(
                company, property.getId(), today.plusDays(2), today.plusDays(4)
        ).roomTypes().get(0).soldRooms()).isEqualTo(1);
        assertThatThrownBy(() -> service.confirmReservation(
                company,
                waitlisted.id(),
                new ReservationLifecycleRequest("Gast akzeptiert", null, ReservationGuaranteeStatus.CREDIT_CARD),
                "Christopher",
                today
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ausgebucht");
    }

    @Test
    void expiresOffersAndKeepsAnAuditableStatusHistory() {
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(5);
        UpsertReservationRequest offerRequest = new UpsertReservationRequest(
                property.getId(), guest.getId(), roomType.getId(), null, ratePlan.getId(),
                today.plusDays(5), today.plusDays(6), 1, 0,
                ReservationStatus.OFFERED, ReservationSource.EMAIL, "Angebot per E-Mail",
                ReservationGuaranteeStatus.UNGUARANTEED, expiredAt
        );

        assertThatThrownBy(() -> service.createReservation(company, offerRequest, "Christopher", today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Haltefrist");

        LocalDateTime futureHold = LocalDateTime.now().plusHours(2);
        UpsertReservationRequest validOffer = new UpsertReservationRequest(
                property.getId(), guest.getId(), roomType.getId(), null, ratePlan.getId(),
                today.plusDays(5), today.plusDays(6), 1, 0,
                ReservationStatus.OFFERED, ReservationSource.EMAIL, "Angebot per E-Mail",
                ReservationGuaranteeStatus.UNGUARANTEED, futureHold
        );
        PmsOperationsResponse created = service.createReservation(company, validOffer, "Christopher", today);
        Long reservationId = created.reservations().get(0).id();

        assertThat(service.expireReservationHolds(futureHold.plusSeconds(1))).isEqualTo(1);
        Reservation expired = reservationStatusHistoryRepository
                .findAllByReservation_IdOrderByChangedAtDesc(reservationId)
                .get(0)
                .getReservation();
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(expired.getCancellationReason()).isEqualTo("Haltefrist abgelaufen");
        assertThat(reservationStatusHistoryRepository
                .findAllByReservation_IdOrderByChangedAtDesc(reservationId))
                .extracting(ReservationStatusHistory::getToStatus)
                .containsExactly(ReservationStatus.CANCELLED, ReservationStatus.OFFERED);
    }

    @Test
    void movesAnInHouseGuestAndMarksThePreviousRoomDirty() {
        Room target = new Room();
        target.setProperty(property);
        target.setRoomType(roomType);
        target.setNumber("102");
        target.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        target = roomRepository.save(target);

        PmsOperationsResponse created = service.createReservation(
                company,
                reservationRequest(today, today.plusDays(1), room.getId()),
                "Christopher",
                today
        );
        Long reservationId = created.reservations().get(0).id();
        service.checkIn(company, reservationId, "Christopher", today);

        PmsOperationsResponse moved = service.moveReservationRoom(
                company,
                reservationId,
                new MoveReservationRoomRequest(target.getId(), "Gastwunsch"),
                "Christopher",
                today
        );

        assertThat(moved.reservations().get(0).roomNumber()).isEqualTo("102");
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getHousekeepingStatus())
                .isEqualTo(HousekeepingStatus.DIRTY);
        assertThat(reservationStatusHistoryRepository
                .findAllByReservation_IdOrderByChangedAtDesc(reservationId).get(0).getReason())
                .contains("101 → 102")
                .contains("Gastwunsch");
    }

    @Test
    void requiresCashShiftAndAuditsRefundsAndClosingVariance() {
        PmsOperationsResponse created = service.createReservation(
                company,
                reservationRequest(today.plusDays(1), today.plusDays(2), room.getId()),
                "Christopher",
                today
        );
        Long folioId = created.folios().get(0).id();

        assertThatThrownBy(() -> service.postPayment(
                company,
                property.getId(),
                folioId,
                new PostPaymentRequest(new BigDecimal("100.00"), PaymentMethod.CASH, "cash-1"),
                "Christopher",
                today
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Kassenschicht");

        service.openCashShift(
                company,
                property.getId(),
                new OpenCashShiftRequest(new BigDecimal("200.00"), "Frühschicht"),
                "Christopher",
                today
        );
        PmsOperationsResponse paid = service.postPayment(
                company,
                property.getId(),
                folioId,
                new PostPaymentRequest(new BigDecimal("100.00"), PaymentMethod.CASH, "cash-1"),
                "Christopher",
                today
        );
        Long paymentId = paid.folios().get(0).paymentEntries().get(0).id();

        PmsOperationsResponse refunded = service.refundPayment(
                company,
                property.getId(),
                paymentId,
                new RefundPaymentRequest(new BigDecimal("25.00"), "Preisnachlass"),
                "Christopher",
                today
        );
        assertThat(refunded.folios().get(0).payments()).isEqualByComparingTo("75.00");
        assertThat(refunded.cashShift().expectedCash()).isEqualByComparingTo("275.00");
        assertThat(refunded.folios().get(0).paymentEntries())
                .extracting(PmsOperationsResponse.PaymentView::kind)
                .containsExactly(PaymentKind.PAYMENT, PaymentKind.REFUND);

        service.closeCashShift(
                company,
                property.getId(),
                new CloseCashShiftRequest(new BigDecimal("274.50"), "50 Rappen Differenz"),
                "Christopher",
                today
        );
        CashShift closed = cashShiftRepository.findAllByProperty_IdOrderByOpenedAtDesc(property.getId()).get(0);
        assertThat(closed.getExpectedCash()).isEqualByComparingTo("275.00");
        assertThat(closed.getVariance()).isEqualByComparingTo("-0.50");
        assertThat(closed.getStatus()).isEqualTo(CashShiftStatus.CLOSED);
        assertThat(paymentRepository.findAllByOriginalPayment_IdAndStatus(paymentId, PaymentStatus.POSTED))
                .hasSize(1);
    }

    @Test
    void maintenanceBlockRemovesInventoryUntilWorkOrderIsResolved() {
        LocalDate start = today.plusDays(10);
        LocalDate end = start.plusDays(2);
        PmsOperationsResponse blocked = service.createMaintenanceWorkOrder(
                company,
                property.getId(),
                new CreateMaintenanceWorkOrderRequest(
                        room.getId(), "Wasserhahn ersetzen", "Leck unter dem Lavabo",
                        MaintenancePriority.HIGH, "Technik", start, true,
                        RoomBlockType.OUT_OF_ORDER, start, end
                ),
                "Christopher",
                today
        );

        assertThat(blocked.maintenanceWorkOrders()).singleElement().satisfies(order -> {
            assertThat(order.status()).isEqualTo(MaintenanceStatus.OPEN);
            assertThat(order.roomBlockId()).isNotNull();
        });
        assertThat(service.getAvailability(company, property.getId(), start, end)
                .roomTypes().get(0).availableRooms()).isZero();
        assertThatThrownBy(() -> service.createReservation(
                company, reservationRequest(start, end, room.getId()), "Christopher", today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ausgebucht");

        Long workOrderId = maintenanceWorkOrderRepository.findAll().get(0).getId();
        service.resolveMaintenanceWorkOrder(
                company,
                property.getId(),
                workOrderId,
                new ResolveMaintenanceWorkOrderRequest("Dichtung ersetzt, Dichtheitsprüfung bestanden"),
                "Christopher",
                today
        );

        assertThat(roomBlockRepository.findAll().get(0).getStatus()).isEqualTo(RoomBlockStatus.COMPLETED);
        assertThat(service.getAvailability(company, property.getId(), start, end)
                .roomTypes().get(0).availableRooms()).isEqualTo(1);
    }

    private UpsertReservationRequest reservationRequest(LocalDate arrival,
                                                        LocalDate departure,
                                                        Long roomId) {
        return new UpsertReservationRequest(
                property.getId(),
                guest.getId(),
                roomType.getId(),
                roomId,
                ratePlan.getId(),
                arrival,
                departure,
                2,
                0,
                ReservationStatus.CONFIRMED,
                ReservationSource.DIRECT,
                "Ruhiges Zimmer"
        );
    }
}
