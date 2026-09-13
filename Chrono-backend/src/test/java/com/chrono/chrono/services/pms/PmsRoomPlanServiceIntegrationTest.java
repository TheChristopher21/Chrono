package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsRoomPlanFilter;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop", showSql = false)
@Import({PmsRoomPlanService.class, PmsSetupService.class})
@ActiveProfiles("test")
class PmsRoomPlanServiceIntegrationTest {
    @Autowired PmsRoomPlanService service;
    @Autowired PmsSetupService setupService;
    @Autowired CompanyRepository companies;
    @Autowired HotelPropertyRepository properties;
    @Autowired RoomRepository rooms;
    @Autowired RoomTypeRepository types;
    @Autowired GuestProfileRepository guests;
    @Autowired RatePlanRepository rates;
    @Autowired ReservationRepository reservations;
    @Autowired RoomBlockRepository blocks;
    private Company company;
    private HotelProperty property;
    private RoomType type;
    private GuestProfile guest;
    private RatePlan rate;
    private final LocalDate start = LocalDate.of(2026, 9, 7);

    @BeforeEach
    void setup() {
        company = companies.save(new Company("Hotelkette"));
        property = property(company, "ZRH");
        type = new RoomType(); type.setProperty(property); type.setCode("DBL"); type.setName("Doppelzimmer");
        type.setBedType("Doppelbett"); type.setMaxOccupancy(2); type = types.save(type);
        guest = new GuestProfile(); guest.setCompany(company); guest.setFirstName("Anna"); guest.setLastName("Gast"); guest = guests.save(guest);
        rate = new RatePlan(); rate.setProperty(property); rate.setRoomType(type); rate.setCode("BAR"); rate.setName("Beste Rate");
        rate.setCurrencyCode("CHF"); rate.setNightlyRate(new java.math.BigDecimal("120.00")); rate = rates.save(rate);
    }

    @Test void usesHotelTodayWithThirtyDaysByDefaultAndRejectsUnboundedRequests() {
        var result = service.getRoomPlan(company, property.getId(), filter(null, List.of(), false, 0, 50, ""));
        assertThat(result.from()).isEqualTo(LocalDate.now(ZoneId.of(property.getTimezone())));
        assertThat(result.to()).isEqualTo(result.from().plusDays(30));
        assertThatThrownBy(() -> service.getRoomPlan(company, property.getId(), filter(start, List.of(), false, 0, 101, "")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    }

    @Test void paginatesAtTheDatabaseAndOrdersNumericRoomNumbers() {
        room("10", "Badewanne"); room("2", "ruhig"); room("1", "");
        var first = service.getRoomPlan(company, property.getId(), filter(start, List.of(), false, 0, 2, ""));
        var second = service.getRoomPlan(company, property.getId(), filter(start, List.of(), false, 1, 2, ""));
        assertThat(first.totalRooms()).isEqualTo(3);
        assertThat(first.rooms()).extracting(r -> r.number()).containsExactly("1", "2");
        assertThat(second.rooms()).extracting(r -> r.number()).containsExactly("10");
        var search = service.getRoomPlan(company, property.getId(), filter(start, List.of(), false, 1, 2, "10"));
        assertThat(search.rooms()).extracting(r -> r.number()).containsExactly("10");
        assertThat(search.page()).isZero();
    }

    @Test void compactSetupUsesDatabaseGroupedCountsAndMatchesFullSetupTotals() {
        room("1", ""); room("2", ""); room("10", "");
        var compact = setupService.getSetup(company, false);
        var full = setupService.getSetup(company, true);
        assertThat(compact.totalRooms()).isEqualTo(3).isEqualTo(full.totalRooms());
        assertThat(compact.properties().get(0).rooms()).isEmpty();
        assertThat(compact.properties().get(0).roomTypes().get(0).roomCount()).isEqualTo(3);
        assertThat(full.properties().get(0).rooms()).hasSize(3);
        assertThat(compact.foundationComplete()).isTrue();
    }

    @Test void combinesExactFeatureTokensWithAndAndKeepsFacetsAcrossPages() {
        Room matching = room("101", "Badewanne, ruhig; Balkon");
        room("102", "Badewanne"); room("103", "keine Badewanne, ruhig");
        var result = service.getRoomPlan(company, property.getId(), filter(start, List.of("Badewanne", "ruhig"), false, 0, 1, ""));
        assertThat(result.rooms()).extracting(r -> r.id()).containsExactly(matching.getId());
        assertThat(result.filters().features()).contains("Badewanne", "ruhig", "Balkon", "keine Badewanne");
        assertThat(result.filters().bedTypes()).containsExactly("Doppelbett");
        assertThat(service.getRoomPlan(company, property.getId(), filter(start, List.of(), false, 0, 50, "%")).rooms()).isEmpty();
    }

    @Test void filtersBedCapacityFloorAndHousekeepingTogether() {
        Room matching = room("101", "Badewanne"); matching.setFloor("3"); matching.setHousekeepingSection("Nord"); rooms.save(matching);
        room("102", "Badewanne");
        var filter = new PmsRoomPlanFilter(start, 10, 0, 50, "", type.getId(), "3", "Doppelbett", "Nord", HousekeepingStatus.CLEAN,
                RoomOperationalStatus.IN_SERVICE, 2, List.of("Badewanne"), false, false);
        assertThat(service.getRoomPlan(company, property.getId(), filter).rooms()).extracting(r -> r.id()).containsExactly(matching.getId());
    }

    @Test void queriesOnlyOverlappingBookingsAndActiveBlocksForTheReturnedRooms() {
        Room first = room("101", ""); Room second = room("102", "");
        Reservation clipped = stay(first, start.minusDays(2), start.plusDays(2), ReservationStatus.CHECKED_IN);
        stay(first, start.minusDays(3), start, ReservationStatus.CONFIRMED);
        stay(first, start.plusDays(30), start.plusDays(32), ReservationStatus.CONFIRMED);
        stay(first, start.plusDays(3), start.plusDays(5), ReservationStatus.CANCELLED);
        stay(second, start, start.plusDays(2), ReservationStatus.CONFIRMED);
        block(first, start.plusDays(4), start.plusDays(6), RoomBlockStatus.ACTIVE, RoomBlockType.OUT_OF_ORDER);
        block(first, start.minusDays(2), start, RoomBlockStatus.ACTIVE, RoomBlockType.OUT_OF_ORDER);
        block(first, start.plusDays(6), start.plusDays(8), RoomBlockStatus.COMPLETED, RoomBlockType.OUT_OF_ORDER);
        var result = service.getRoomPlan(company, property.getId(), filter(start, List.of(), false, 0, 1, ""));
        assertThat(result.reservations()).extracting(r -> r.id()).containsExactly(clipped.getId());
        assertThat(result.reservations().get(0).arrivalDate()).isEqualTo(start.minusDays(2));
        assertThat(result.blocks()).hasSize(1);
    }

    @Test void availabilityRespectsFullDateRangeAndExclusiveDepartures() {
        Room free = room("101", "");
        stay(free, start.minusDays(3), start, ReservationStatus.CONFIRMED);
        stay(free, start, start.plusDays(3), ReservationStatus.CANCELLED);
        block(free, start.plusDays(1), start.plusDays(2), RoomBlockStatus.ACTIVE, RoomBlockType.OUT_OF_SERVICE);
        Room booked = room("102", ""); stay(booked, start.plusDays(20), start.plusDays(21), ReservationStatus.CONFIRMED);
        Room blocked = room("103", ""); block(blocked, start.plusDays(20), start.plusDays(21), RoomBlockStatus.ACTIVE, RoomBlockType.OWNER_USE);
        Room out = room("104", ""); out.setOperationalStatus(RoomOperationalStatus.OUT_OF_ORDER); rooms.save(out);
        assertThat(service.getRoomPlan(company, property.getId(), filter(start, List.of(), true, 0, 50, "")).rooms()).extracting(r -> r.id()).containsExactly(free.getId());
    }

    @Test void preventsAccessAcrossCompanyAndPropertyBoundaries() {
        Room ownRoom = room("101", "Badewanne");
        Company foreignCompany = companies.save(new Company("Fremdes Hotel"));
        HotelProperty foreignProperty = property(foreignCompany, "OTHER");
        HotelProperty sibling = property(company, "BER");
        Room siblingRoom = room("201", "Nur Berlin"); siblingRoom.setProperty(sibling); rooms.save(siblingRoom);
        var result = service.getRoomPlan(company, property.getId(), filter(start, List.of(), false, 0, 50, ""));
        assertThat(result.rooms()).extracting(r -> r.id()).containsExactly(ownRoom.getId());
        assertThat(result.filters().features()).doesNotContain("Nur Berlin");
        assertThatThrownBy(() -> service.getRoomPlan(company, foreignProperty.getId(), filter(start, List.of(), false, 0, 50, "")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test void showsEachStaySegmentOnItsOwnRoomWithOriginalBookingDatesAndExclusiveBoundaries() {
        Room oldRoom = room("101", ""); Room newRoom = room("102", "");
        Reservation moved = stay(oldRoom, start, start.plusDays(10), ReservationStatus.CONFIRMED);
        addSegment(moved, oldRoom, start, start.plusDays(4));
        addSegment(moved, newRoom, start.plusDays(4), start.plusDays(10));
        reservations.saveAndFlush(moved);
        var timeline = service.getRoomPlan(company, property.getId(), filter(start, List.of(), false, 0, 50, ""));
        assertThat(timeline.reservations()).hasSize(2).allSatisfy(row -> {
            assertThat(row.id()).isEqualTo(moved.getId()); assertThat(row.arrivalDate()).isEqualTo(start);
            assertThat(row.departureDate()).isEqualTo(start.plusDays(10)); assertThat(row.segmentId()).isNotNull();
        });
        assertThat(timeline.reservations()).anySatisfy(row -> {
            assertThat(row.roomId()).isEqualTo(oldRoom.getId()); assertThat(row.segmentStartDate()).isEqualTo(start); assertThat(row.segmentEndDate()).isEqualTo(start.plusDays(4));
        }).anySatisfy(row -> {
            assertThat(row.roomId()).isEqualTo(newRoom.getId()); assertThat(row.segmentStartDate()).isEqualTo(start.plusDays(4)); assertThat(row.segmentEndDate()).isEqualTo(start.plusDays(10));
        });
        var afterMove = service.getRoomPlan(company, property.getId(), shortFilter(start.plusDays(4), 3, false));
        assertThat(afterMove.reservations()).singleElement().satisfies(row -> assertThat(row.roomId()).isEqualTo(newRoom.getId()));
    }

    @Test void availabilityExcludesOnlyTheOverlappingStaySegmentAndAlsoRespectsLegacyReservations() {
        Room oldRoom = room("101", ""); Room newRoom = room("102", ""); Room other = room("103", "");
        Reservation moved = stay(oldRoom, start, start.plusDays(10), ReservationStatus.CONFIRMED);
        addSegment(moved, oldRoom, start, start.plusDays(4)); addSegment(moved, newRoom, start.plusDays(4), start.plusDays(10));
        reservations.saveAndFlush(moved);
        stay(other, start, start.plusDays(10), ReservationStatus.CONFIRMED);
        assertThat(service.getRoomPlan(company, property.getId(), shortFilter(start, 4, true)).rooms()).extracting(r -> r.id()).containsExactly(newRoom.getId());
        assertThat(service.getRoomPlan(company, property.getId(), shortFilter(start.plusDays(4), 6, true)).rooms()).extracting(r -> r.id()).containsExactly(oldRoom.getId());
        assertThat(service.getRoomPlan(company, property.getId(), shortFilter(start.plusDays(3), 2, true)).rooms()).isEmpty();
        assertThat(service.getRoomPlan(company, property.getId(), shortFilter(start.plusDays(10), 1, true)).rooms()).hasSize(3);
        var excluded = java.util.Set.of(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW, ReservationStatus.CHECKED_OUT, ReservationStatus.OFFERED, ReservationStatus.WAITLISTED);
        assertThat(reservations.countOverlappingByRoom(newRoom.getId(), start, start.plusDays(4), excluded, null)).isZero();
        assertThat(reservations.countOverlappingByRoom(oldRoom.getId(), start.plusDays(4), start.plusDays(10), excluded, null)).isZero();
        assertThat(reservations.countOverlappingByRoom(newRoom.getId(), start.plusDays(4), start.plusDays(5), excluded, null)).isEqualTo(1);
    }

    private PmsRoomPlanFilter shortFilter(LocalDate from, int days, boolean available) {
        return new PmsRoomPlanFilter(from, days, 0, 50, "", null, null, null, null, null, null, null, List.of(), available, false);
    }
    private void addSegment(Reservation reservation, Room room, LocalDate from, LocalDate to) {
        ReservationRoomSegment segment = new ReservationRoomSegment(); segment.setReservation(reservation); segment.setRoom(room); segment.setRatePlan(rate);
        segment.setStartDate(from); segment.setEndDate(to); segment.setCreatedBy("Test"); reservation.getRoomSegments().add(segment);
    }

    private PmsRoomPlanFilter filter(LocalDate from, List<String> features, boolean available, int page, int size, String search) {
        return new PmsRoomPlanFilter(from, 30, page, size, search, null, null, null, null, null, null, null, features, available, false);
    }
    private HotelProperty property(Company owner, String code) {
        HotelProperty p = new HotelProperty(); p.setCompany(owner); p.setCode(code); p.setName(code); p.setTimezone("Pacific/Auckland"); return properties.save(p);
    }
    private Room room(String number, String features) {
        Room r = new Room(); r.setProperty(property); r.setRoomType(type); r.setNumber(number); r.setFeatures(features); return rooms.save(r);
    }
    private Reservation stay(Room room, LocalDate from, LocalDate to, ReservationStatus status) {
        Reservation r = new Reservation(); r.setProperty(property); r.setRoomType(type); r.setRoom(room); r.setRatePlan(rate); r.setGuest(guest);
        r.setConfirmationCode("CHR-" + java.util.UUID.randomUUID().toString().substring(0, 20)); r.setArrivalDate(from); r.setDepartureDate(to);
        r.setStatus(status); r.setCurrencyCode("CHF"); r.setCreatedBy("Test"); return reservations.save(r);
    }
    private void block(Room room, LocalDate from, LocalDate to, RoomBlockStatus status, RoomBlockType type) {
        RoomBlock b = new RoomBlock(); b.setProperty(property); b.setRoom(room); b.setStartDate(from); b.setEndDate(to); b.setType(type);
        b.setStatus(status); b.setReason("Wartung"); b.setCreatedBy("Test"); blocks.save(b);
    }
}
