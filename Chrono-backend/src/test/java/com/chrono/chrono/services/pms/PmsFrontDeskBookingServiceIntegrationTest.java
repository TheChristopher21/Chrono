package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.CompleteGuestRegistrationRequest;
import com.chrono.chrono.dto.pms.CreateFrontDeskBookingRequest;
import com.chrono.chrono.dto.pms.FrontDeskBookingResponse;
import com.chrono.chrono.dto.pms.AvailabilityResponse;
import com.chrono.chrono.dto.pms.UpsertGuestRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.GuestRegistrationStatus;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.HousekeepingStatus;
import com.chrono.chrono.entities.pms.RatePlan;
import com.chrono.chrono.entities.pms.ReservationGuaranteeStatus;
import com.chrono.chrono.entities.pms.ReservationSource;
import com.chrono.chrono.entities.pms.ReservationStatus;
import com.chrono.chrono.entities.pms.Room;
import com.chrono.chrono.entities.pms.RoomType;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.pms.FrontDeskBookingRequestRepository;
import com.chrono.chrono.repositories.pms.GuestProfileRepository;
import com.chrono.chrono.repositories.pms.GuestRegistrationRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.RatePlanRepository;
import com.chrono.chrono.repositories.pms.ReservationRepository;
import com.chrono.chrono.repositories.pms.RoomRepository;
import com.chrono.chrono.repositories.pms.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({
        PmsOperationsService.class,
        PmsAdvancedService.class,
        PmsFrontDeskBookingService.class,
        PmsAuditWriter.class,
        PmsDocumentFingerprintService.class
})
@ActiveProfiles("test")
class PmsFrontDeskBookingServiceIntegrationTest {
    @Autowired private PmsFrontDeskBookingService service;
    @Autowired private PmsOperationsService operationsService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private HotelPropertyRepository propertyRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RatePlanRepository ratePlanRepository;
    @Autowired private GuestProfileRepository guestRepository;
    @Autowired private GuestRegistrationRepository registrationRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private FrontDeskBookingRequestRepository requestRepository;

    private Company company;
    private HotelProperty property;
    private RoomType roomType;
    private Room room;
    private RatePlan ratePlan;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Front Desk Test AG"));
        property = new HotelProperty();
        property.setCompany(company);
        property.setCode("FD-" + company.getId());
        property.setName("Front Desk Hotel");
        property.setTimezone("Europe/Zurich");
        property.setCurrencyCode("CHF");
        property = propertyRepository.save(property);

        roomType = new RoomType();
        roomType.setProperty(property);
        roomType.setCode("DBL");
        roomType.setName("Doppelzimmer");
        roomType.setMaxOccupancy(2);
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
        ratePlan.setName("Beste verfügbare Rate");
        ratePlan.setCurrencyCode("CHF");
        ratePlan.setNightlyRate(new BigDecimal("145.00"));
        ratePlan.setMinStay(1);
        ratePlan = ratePlanRepository.save(ratePlan);
        today = LocalDate.now(ZoneId.of(property.getTimezone()));
    }

    @Test
    void createsRegisteredWalkInAndChecksInAtomically() {
        FrontDeskBookingResponse response = service.createBooking(
                company,
                property.getId(),
                "front-desk-test-0001",
                request("Erster Aufenthalt", true, true),
                "reception.one",
                today
        );

        assertThat(response.guestId()).isNotNull();
        assertThat(response.reservationId()).isNotNull();
        assertThat(response.folioId()).isNotNull();
        assertThat(response.confirmationCode()).startsWith("CHR-");
        assertThat(response.totalAmount()).isEqualByComparingTo("145.00");
        assertThat(response.balance()).isEqualByComparingTo("145.00");
        assertThat(response.registrationStatus()).isEqualTo(GuestRegistrationStatus.COMPLETED);
        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(response.checkInReady()).isTrue();
        assertThat(response.checkInBlockers()).isEmpty();
        assertThat(response.operations().reservations()).singleElement()
                .satisfies(reservation -> assertThat(reservation.id()).isEqualTo(response.reservationId()));
        assertThat(registrationRepository.findByReservation_Id(response.reservationId())).isPresent();
    }

    @Test
    void availabilityExposesActuallyFreeRoomsAndCurrentCheckInReadiness() {
        AvailabilityResponse availability = operationsService.getAvailability(
                company, property.getId(), today, today.plusDays(1));

        assertThat(availability.roomTypes()).singleElement().satisfies(type -> {
            assertThat(type.freeRooms()).singleElement().satisfies(availableRoom -> {
                assertThat(availableRoom.roomId()).isEqualTo(room.getId());
                assertThat(availableRoom.roomNumber()).isEqualTo("101");
                assertThat(availableRoom.checkInReady()).isTrue();
            });
        });

        service.createBooking(company, property.getId(), "front-desk-availability",
                request("Belegt", false, false), "reception.one", today);
        AvailabilityResponse afterBooking = operationsService.getAvailability(
                company, property.getId(), today, today.plusDays(1));
        assertThat(afterBooking.roomTypes()).singleElement()
                .satisfies(type -> assertThat(type.freeRooms()).isEmpty());
    }

    @Test
    void safelyReplaysSameRequestWithoutCreatingDuplicates() {
        CreateFrontDeskBookingRequest request = request("Replay", true, false);
        FrontDeskBookingResponse first = service.createBooking(
                company, property.getId(), "front-desk-replay-01", request, "reception.one", today);
        FrontDeskBookingResponse replay = service.createBooking(
                company, property.getId(), "front-desk-replay-01", request, "reception.two", today);

        assertThat(replay.reservationId()).isEqualTo(first.reservationId());
        assertThat(replay.guestId()).isEqualTo(first.guestId());
        assertThat(requestRepository.findByProperty_IdAndIdempotencyKey(
                property.getId(), "front-desk-replay-01")).isPresent();
        assertThat(reservationRepository
                .findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
                        property.getId(), today.plusDays(1), today.minusDays(1)))
                .hasSize(1);
        assertThat(guestRepository.findAllByCompany_IdOrderByLastNameAscFirstNameAsc(company.getId())).hasSize(1);
    }

    @Test
    void rejectsSameIdempotencyKeyForDifferentPayload() {
        service.createBooking(company, property.getId(), "front-desk-conflict-1",
                request("Original", true, false), "reception.one", today);

        assertThatThrownBy(() -> service.createBooking(
                company,
                property.getId(),
                "front-desk-conflict-1",
                request("Verändert", true, false),
                "reception.one",
                today
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("andere Rezeptionsbuchung");
        assertThat(reservationRepository
                .findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
                        property.getId(), today.plusDays(1), today.minusDays(1)))
                .hasSize(1);
    }

    @Test
    void requiresRegistrationForImmediateCheckIn() {
        assertThatThrownBy(() -> service.createBooking(
                company,
                property.getId(),
                "front-desk-no-registration",
                request("Ohne Meldedaten", false, true),
                "reception.one",
                today
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Meldedaten");
        assertThat(guestRepository.findAllByCompany_IdOrderByLastNameAscFirstNameAsc(company.getId())).isEmpty();
    }

    @Test
    void requiresEmailOrPhoneForNewGuest() {
        CreateFrontDeskBookingRequest base = request("Ohne Kontakt", false, false);
        CreateFrontDeskBookingRequest invalid = copy(
                base,
                new UpsertGuestRequest(
                        "Nina", "Meier", " ", null, LocalDate.of(1990, 5, 12),
                        "CH", "de", "Direktbuchung", false
                ),
                base.roomId(),
                base.source(),
                base.checkInNow()
        );

        assertThatThrownBy(() -> service.createBooking(
                company, property.getId(), "front-desk-no-contact", invalid, "reception.one", today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("E-Mail-Adresse oder Telefonnummer");
        assertThat(guestRepository.findAllByCompany_IdOrderByLastNameAscFirstNameAsc(company.getId())).isEmpty();
    }

    @Test
    void couplesWalkInSourceExactlyToImmediateCheckIn() {
        CreateFrontDeskBookingRequest futureWalkInBase = request("Walk-in ohne Check-in", false, false);
        CreateFrontDeskBookingRequest futureWalkIn = copy(
                futureWalkInBase,
                futureWalkInBase.newGuest(),
                futureWalkInBase.roomId(),
                ReservationSource.WALK_IN,
                false
        );
        assertThatThrownBy(() -> service.createBooking(
                company, property.getId(), "front-desk-walkin-later", futureWalkIn, "reception.one", today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("WALK_IN");

        CreateFrontDeskBookingRequest immediateDirectBase = request("Direkt mit Check-in", true, true);
        CreateFrontDeskBookingRequest immediateDirect = copy(
                immediateDirectBase,
                immediateDirectBase.newGuest(),
                immediateDirectBase.roomId(),
                ReservationSource.DIRECT,
                true
        );
        assertThatThrownBy(() -> service.createBooking(
                company, property.getId(), "front-desk-direct-now", immediateDirect, "reception.one", today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("WALK_IN");

        assertThat(reservationRepository
                .findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
                        property.getId(), today.plusDays(1), today.minusDays(1)))
                .isEmpty();
    }

    @Test
    void requiresAssignedRoomForImmediateCheckIn() {
        CreateFrontDeskBookingRequest base = request("Ohne Zimmer", true, true);
        CreateFrontDeskBookingRequest invalid = copy(
                base,
                base.newGuest(),
                null,
                base.source(),
                true
        );

        assertThatThrownBy(() -> service.createBooking(
                company, property.getId(), "front-desk-no-room", invalid, "reception.one", today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Zimmer zugewiesen");
        assertThat(guestRepository.findAllByCompany_IdOrderByLastNameAscFirstNameAsc(company.getId())).isEmpty();
    }

    @Test
    void rejectsSystemOwnedBookingSources() {
        CreateFrontDeskBookingRequest base = request("Falsche Quelle", false, false);
        CreateFrontDeskBookingRequest invalid = new CreateFrontDeskBookingRequest(
                base.existingGuestId(), base.newGuest(), base.roomTypeId(), base.roomId(), base.ratePlanId(),
                base.arrivalDate(), base.departureDate(), base.adults(), base.children(),
                ReservationSource.BOOKING_ENGINE, base.guaranteeStatus(), base.notes(), base.registration(), false);

        assertThatThrownBy(() -> service.createBooking(
                company, property.getId(), "front-desk-source-0001", invalid, "reception.one", today))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Buchungsquelle");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackGuestReservationRegistrationAndIdempotencyWhenCheckInFails() {
        room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        roomRepository.save(room);

        assertThatThrownBy(() -> service.createBooking(
                company,
                property.getId(),
                "front-desk-rollback-01",
                request("Rollback", true, true),
                "reception.one",
                today
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("sauber");

        assertThat(guestRepository.findAllByCompany_IdOrderByLastNameAscFirstNameAsc(company.getId())).isEmpty();
        assertThat(reservationRepository
                .findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
                        property.getId(), today.plusDays(1), today.minusDays(1)))
                .isEmpty();
        assertThat(requestRepository.findByProperty_IdAndIdempotencyKey(
                property.getId(), "front-desk-rollback-01")).isEmpty();
    }

    private CreateFrontDeskBookingRequest request(String notes, boolean withRegistration, boolean checkInNow) {
        return new CreateFrontDeskBookingRequest(
                null,
                new UpsertGuestRequest(
                        "Nina",
                        "Meier",
                        "nina.meier@example.com",
                        "+41790000000",
                        LocalDate.of(1990, 5, 12),
                        "CH",
                        "de",
                        "Walk-in",
                        false
                ),
                roomType.getId(),
                room.getId(),
                ratePlan.getId(),
                today,
                today.plusDays(1),
                1,
                0,
                checkInNow ? ReservationSource.WALK_IN : ReservationSource.DIRECT,
                ReservationGuaranteeStatus.UNGUARANTEED,
                notes,
                withRegistration ? registration() : null,
                checkInNow
        );
    }

    private CreateFrontDeskBookingRequest copy(
            CreateFrontDeskBookingRequest base,
            UpsertGuestRequest newGuest,
            Long roomId,
            ReservationSource source,
            boolean checkInNow) {
        return new CreateFrontDeskBookingRequest(
                base.existingGuestId(),
                newGuest,
                base.roomTypeId(),
                roomId,
                base.ratePlanId(),
                base.arrivalDate(),
                base.departureDate(),
                base.adults(),
                base.children(),
                source,
                base.guaranteeStatus(),
                base.notes(),
                base.registration(),
                checkInNow
        );
    }

    private CompleteGuestRegistrationRequest registration() {
        return new CompleteGuestRegistrationRequest(
                "Seestrasse 10",
                "8002",
                "Zürich",
                "CH",
                "CH",
                "X123456789",
                null,
                "Nina Meier",
                true,
                null,
                null
        );
    }
}
