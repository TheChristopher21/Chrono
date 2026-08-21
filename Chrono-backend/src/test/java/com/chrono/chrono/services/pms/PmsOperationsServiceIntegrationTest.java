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
    private ReservationRepository reservationRepository;
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
    void dashboardSeparatesOperationalArrivalsFromOptionsAndUsesTheSelectedBusinessDateForOccupancy() {
        Room checkedInRoom = room("102");
        Room optionRoom = room("103");
        LocalDate departure = today.plusDays(3);

        service.createReservation(
                company,
                reservationRequest(today, departure, room.getId()),
                "Christopher",
                today
        );
        PmsOperationsResponse checkedInCreated = service.createReservation(
                company,
                reservationRequest(today, departure, checkedInRoom.getId()),
                "Christopher",
                today
        );
        Long checkedInReservationId = checkedInCreated.reservations().stream()
                .filter(view -> checkedInRoom.getId().equals(view.roomId()))
                .map(PmsOperationsResponse.ReservationView::id)
                .findFirst()
                .orElseThrow();
        service.checkIn(company, checkedInReservationId, "Christopher", today);

        service.createReservation(
                company,
                reservationRequest(today, departure, optionRoom.getId(), ReservationStatus.TENTATIVE),
                "Christopher",
                today
        );
        service.createReservation(
                company,
                reservationRequest(today, departure, null, ReservationStatus.OFFERED),
                "Christopher",
                today
        );
        service.createReservation(
                company,
                reservationRequest(today, departure, null, ReservationStatus.WAITLISTED),
                "Christopher",
                today
        );

        PmsOperationsResponse todayView =
                service.getOperations(company, property.getId(), today, null, null);
        assertThat(todayView.arrivals())
                .extracting(PmsOperationsResponse.ReservationView::status)
                .containsExactlyInAnyOrder(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);
        assertThat(todayView.metrics().arrivals()).isEqualTo(2);
        assertThat(todayView.metrics().occupiedRooms()).isEqualTo(2);
        assertThat(todayView.metrics().availableRooms()).isEqualTo(1);
        assertThat(todayView.metrics().occupancyPercent()).isEqualTo(67);
        assertThat(todayView.metrics().inHouse()).isEqualTo(1);

        PmsOperationsResponse nextBusinessDate =
                service.getOperations(company, property.getId(), today.plusDays(1), null, null);
        assertThat(nextBusinessDate.arrivals()).isEmpty();
        assertThat(nextBusinessDate.metrics().occupiedRooms()).isEqualTo(2);
        assertThat(nextBusinessDate.metrics().availableRooms()).isEqualTo(1);
        assertThat(nextBusinessDate.metrics().occupancyPercent()).isEqualTo(67);
        assertThat(nextBusinessDate.metrics().inHouse()).isEqualTo(1);
    }

    @Test
    void dashboardDeparturesContainOnlyGuestsInHouseAndCompletedCheckouts() {
        Room checkedOutRoom = room("102");
        Room optionRoom = room("103");
        LocalDate departure = today.plusDays(1);

        PmsOperationsResponse checkedInCreated = service.createReservation(
                company,
                reservationRequest(today, departure, room.getId()),
                "Christopher",
                today
        );
        Long checkedInReservationId = checkedInCreated.reservations().stream()
                .filter(view -> room.getId().equals(view.roomId()))
                .map(PmsOperationsResponse.ReservationView::id)
                .findFirst()
                .orElseThrow();
        service.checkIn(company, checkedInReservationId, "Christopher", today);

        PmsOperationsResponse checkedOutCreated = service.createReservation(
                company,
                reservationRequest(today, departure, checkedOutRoom.getId(), ReservationStatus.OFFERED),
                "Christopher",
                today
        );
        Long checkedOutReservationId = checkedOutCreated.reservations().stream()
                .filter(view -> checkedOutRoom.getId().equals(view.roomId()))
                .map(PmsOperationsResponse.ReservationView::id)
                .findFirst()
                .orElseThrow();
        Reservation checkedOut = reservationRepository.findById(checkedOutReservationId).orElseThrow();
        checkedOut.setStatus(ReservationStatus.CHECKED_OUT);
        checkedOut.setCheckedOutAt(LocalDateTime.now());
        reservationRepository.save(checkedOut);

        service.createReservation(
                company,
                reservationRequest(today, departure, optionRoom.getId(), ReservationStatus.TENTATIVE),
                "Christopher",
                today
        );
        service.createReservation(
                company,
                reservationRequest(today, departure, null, ReservationStatus.OFFERED),
                "Christopher",
                today
        );
        service.createReservation(
                company,
                reservationRequest(today, departure, null, ReservationStatus.WAITLISTED),
                "Christopher",
                today
        );

        PmsOperationsResponse departureView =
                service.getOperations(company, property.getId(), departure, null, null);
        assertThat(departureView.departures())
                .extracting(PmsOperationsResponse.ReservationView::status)
                .containsExactlyInAnyOrder(ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT);
        assertThat(departureView.metrics().departures()).isEqualTo(2);
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

    @Test
    void outOfServiceBlockKeepsTheRoomInInventoryAndAssignable() {
        LocalDate start = today.plusDays(10);
        LocalDate end = start.plusDays(2);

        service.createMaintenanceWorkOrder(
                company,
                property.getId(),
                new CreateMaintenanceWorkOrderRequest(
                        room.getId(), "Kosmetischer Mangel", "Kratzer am Nachttisch",
                        MaintenancePriority.LOW, "Technik", start, true,
                        RoomBlockType.OUT_OF_SERVICE, start, end
                ),
                "Christopher",
                today
        );

        assertThat(roomBlockRepository.findAll()).singleElement().satisfies(block -> {
            assertThat(block.getType()).isEqualTo(RoomBlockType.OUT_OF_SERVICE);
            assertThat(block.getStatus()).isEqualTo(RoomBlockStatus.ACTIVE);
        });
        assertThat(service.getAvailability(company, property.getId(), start, end)
                .roomTypes().get(0).availableRooms()).isEqualTo(1);

        PmsOperationsResponse reservation = service.createReservation(
                company,
                reservationRequest(start, end, room.getId()),
                "Christopher",
                today
        );
        assertThat(reservation.reservations()).singleElement()
                 .satisfies(view -> assertThat(view.roomNumber()).isEqualTo("101"));
    }

    @Test
    void dashboardInventoryDistinguishesOutOfServiceFromBlockingRoomStatuses() {
        RoomBlock outOfService = roomBlock(RoomBlockType.OUT_OF_SERVICE, today, today.plusDays(1));

        PmsOperationsResponse withOutOfService =
                service.getOperations(company, property.getId(), today, null, null);
        assertThat(withOutOfService.metrics().totalRooms()).isEqualTo(1);
        assertThat(withOutOfService.metrics().availableRooms()).isEqualTo(1);

        roomBlockRepository.delete(outOfService);
        roomBlockRepository.flush();
        room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        roomRepository.save(room);
        roomBlock(RoomBlockType.OUT_OF_ORDER, today, today.plusDays(1));

        PmsOperationsResponse withOutOfOrder =
                service.getOperations(company, property.getId(), today, null, null);
        assertThat(withOutOfOrder.metrics().totalRooms()).isZero();
        assertThat(withOutOfOrder.metrics().availableRooms()).isZero();
        assertThat(withOutOfOrder.metrics().occupancyPercent()).isZero();
        assertThat(withOutOfOrder.metrics().dirtyRooms()).isZero();

        roomBlockRepository.deleteAll();
        roomBlockRepository.flush();
        roomBlock(RoomBlockType.OWNER_USE, today, today.plusDays(1));

        PmsOperationsResponse withOwnerUse =
                service.getOperations(company, property.getId(), today, null, null);
        assertThat(withOwnerUse.metrics().totalRooms()).isZero();
        assertThat(withOwnerUse.metrics().availableRooms()).isZero();
    }

    @Test
    void housekeepingStatusDoesNotChangeTheOperationalRoomStatus() {
        HousekeepingTask task = new HousekeepingTask();
        task.setProperty(property);
        task.setRoom(room);
        task.setServiceDate(today);
        task.setType(HousekeepingTaskType.MANUAL);
        task.setStatus(HousekeepingStatus.DIRTY);
        task.setPriority(50);
        task.setEstimatedMinutes(20);
        task = housekeepingTaskRepository.save(task);

        room.setOperationalStatus(RoomOperationalStatus.OUT_OF_ORDER);
        roomRepository.save(room);

        service.updateHousekeepingTask(
                company,
                property.getId(),
                task.getId(),
                new UpdateHousekeepingTaskRequest(
                        HousekeepingTaskType.INSPECTION,
                        HousekeepingStatus.CLEAN,
                        50,
                        20,
                        null,
                        "Housekeeping"
                ),
                today
        );

        Room technicallyBlocked = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(technicallyBlocked.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.CLEAN);
        assertThat(technicallyBlocked.getOperationalStatus()).isEqualTo(RoomOperationalStatus.OUT_OF_ORDER);

        technicallyBlocked.setOperationalStatus(RoomOperationalStatus.IN_SERVICE);
        roomRepository.save(technicallyBlocked);

        service.updateHousekeepingTask(
                company,
                property.getId(),
                task.getId(),
                new UpdateHousekeepingTaskRequest(
                        HousekeepingTaskType.MANUAL,
                        HousekeepingStatus.OUT_OF_SERVICE,
                        50,
                        20,
                        null,
                        "Housekeeping"
                ),
                today
        );

        Room housekeepingBlocked = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(housekeepingBlocked.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.OUT_OF_SERVICE);
        assertThat(housekeepingBlocked.getOperationalStatus()).isEqualTo(RoomOperationalStatus.IN_SERVICE);
    }

    @Test
    void dirtyRoomMetricOnlyCountsSellableRoomsAwaitingCleaning() {
        room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        roomRepository.save(room);
        assertThat(service.getOperations(company, property.getId(), today, null, null)
                .metrics().dirtyRooms()).isEqualTo(1);

        room.setHousekeepingStatus(HousekeepingStatus.IN_PROGRESS);
        roomRepository.save(room);
        assertThat(service.getOperations(company, property.getId(), today, null, null)
                .metrics().dirtyRooms()).isZero();

        room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        room.setOperationalStatus(RoomOperationalStatus.OUT_OF_ORDER);
        roomRepository.save(room);
        assertThat(service.getOperations(company, property.getId(), today, null, null)
                .metrics().dirtyRooms()).isZero();
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

    private UpsertReservationRequest reservationRequest(LocalDate arrival,
                                                        LocalDate departure,
                                                        Long roomId,
                                                        ReservationStatus status) {
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
                status,
                ReservationSource.DIRECT,
                "Ruhiges Zimmer",
                ReservationGuaranteeStatus.UNGUARANTEED,
                null
        );
    }

    private Room room(String number) {
        Room additionalRoom = new Room();
        additionalRoom.setProperty(property);
        additionalRoom.setRoomType(roomType);
        additionalRoom.setNumber(number);
        additionalRoom.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        return roomRepository.save(additionalRoom);
    }

    private RoomBlock roomBlock(RoomBlockType type, LocalDate startDate, LocalDate endDate) {
        RoomBlock block = new RoomBlock();
        block.setProperty(property);
        block.setRoom(room);
        block.setType(type);
        block.setStartDate(startDate);
        block.setEndDate(endDate);
        block.setReason("Wartung");
        block.setCreatedBy("Christopher");
        return roomBlockRepository.save(block);
    }
}
