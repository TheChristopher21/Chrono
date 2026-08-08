package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsPerformanceReportResponse;
import com.chrono.chrono.dto.pms.PmsPortfolioResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(PmsReportingService.class)
@ActiveProfiles("test")
class PmsReportingServiceIntegrationTest {

    @Autowired private PmsReportingService service;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private HotelPropertyRepository propertyRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RatePlanRepository ratePlanRepository;
    @Autowired private GuestProfileRepository guestRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private RoomBlockRepository roomBlockRepository;

    private Company company;
    private HotelProperty property;
    private Room room102;
    private LocalDate from;

    @BeforeEach
    void setUp() {
        from = LocalDate.of(2026, 7, 28);
        company = companyRepository.save(new Company("Chrono Hotel AG"));

        property = new HotelProperty();
        property.setCompany(company);
        property.setCode("ZRH");
        property.setName("Chrono Zürich");
        property.setLegalName("Chrono Hotel AG");
        property.setAddressLine1("Musterstrasse 1");
        property.setPostalCode("8000");
        property.setCity("Zürich");
        property.setCountryCode("CH");
        property.setTimezone("Europe/Zurich");
        property.setCurrencyCode("CHF");
        property = propertyRepository.save(property);

        RoomType type = new RoomType();
        type.setProperty(property);
        type.setCode("DBL");
        type.setName("Doppelzimmer");
        type.setBaseOccupancy(1);
        type.setMaxOccupancy(2);
        type = roomTypeRepository.save(type);

        Room room101 = room(type, "101");
        room102 = room(type, "102");

        RatePlan rate = new RatePlan();
        rate.setProperty(property);
        rate.setRoomType(type);
        rate.setCode("BAR");
        rate.setName("Beste Rate");
        rate.setNightlyRate(new BigDecimal("100.00"));
        rate.setCurrencyCode("CHF");
        rate = ratePlanRepository.save(rate);

        GuestProfile guest = new GuestProfile();
        guest.setCompany(company);
        guest.setFirstName("Gabriela");
        guest.setLastName("Tschopp");
        guest.setEmail("gabriela@example.com");
        guest = guestRepository.save(guest);

        Reservation reservation = new Reservation();
        reservation.setProperty(property);
        reservation.setGuest(guest);
        reservation.setRoomType(type);
        reservation.setRoom(room101);
        reservation.setRatePlan(rate);
        reservation.setConfirmationCode("CHR-REPORT");
        reservation.setArrivalDate(from);
        reservation.setDepartureDate(from.plusDays(2));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setSource(ReservationSource.DIRECT);
        reservation.setTotalAmount(new BigDecimal("200.00"));
        reservation.setCurrencyCode("CHF");
        reservation.setCreatedBy("Christopher");
        reservationRepository.save(reservation);

        roomBlock(room102, RoomBlockType.OUT_OF_ORDER, from.plusDays(1), from.plusDays(2));
    }

    @Test
    void calculatesOccupancyAdrRevParAndSourceMixByStayDate() {
        PmsPerformanceReportResponse report = service.performance(
                company, property.getId(), from, from.plusDays(2));

        assertThat(report.availableRoomNights()).isEqualTo(3);
        assertThat(report.soldRoomNights()).isEqualTo(2);
        assertThat(report.occupancyPercent()).isEqualByComparingTo("66.67");
        assertThat(report.roomRevenue()).isEqualByComparingTo("200.00");
        assertThat(report.adr()).isEqualByComparingTo("100.00");
        assertThat(report.revPar()).isEqualByComparingTo("66.67");
        assertThat(report.daily()).extracting(PmsPerformanceReportResponse.DailyPerformance::availableRooms)
                .containsExactly(2L, 1L);
        assertThat(report.sources()).singleElement().satisfies(source -> {
            assertThat(source.source()).isEqualTo(ReservationSource.DIRECT);
            assertThat(source.soldRoomNights()).isEqualTo(2);
            assertThat(source.sharePercent()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void rejectsUnboundedReportingPeriods() {
        assertThatThrownBy(() -> service.performance(
                company, property.getId(), from, from.plusDays(367)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("366");
    }

    @Test
    void aggregatesPortfolioOperationsWithoutMixingCurrencies() {
        PmsPortfolioResponse portfolio = service.portfolio(company, from);

        assertThat(portfolio.properties()).isEqualTo(1);
        assertThat(portfolio.operationalRooms()).isEqualTo(2);
        assertThat(portfolio.availableRooms()).isEqualTo(2);
        assertThat(portfolio.soldRooms()).isEqualTo(1);
        assertThat(portfolio.occupancyPercent()).isEqualByComparingTo("50.00");
        assertThat(portfolio.arrivals()).isEqualTo(1);
        assertThat(portfolio.hotels()).singleElement().satisfies(hotel -> {
            assertThat(hotel.currencyCode()).isEqualTo("CHF");
            assertThat(hotel.timezone()).isEqualTo("Europe/Zurich");
        });
    }

    @Test
    void outOfServiceRemainsInAvailableInventory() {
        Room occupiedButSellable = reservationRepository.findAll().get(0).getRoom();
        roomBlock(occupiedButSellable, RoomBlockType.OUT_OF_SERVICE, from, from.plusDays(2));

        PmsPerformanceReportResponse report = service.performance(
                company, property.getId(), from, from.plusDays(2));
        PmsPortfolioResponse portfolio = service.portfolio(company, from);

        assertThat(report.availableRoomNights()).isEqualTo(3);
        assertThat(report.daily())
                .extracting(PmsPerformanceReportResponse.DailyPerformance::availableRooms)
                .containsExactly(2L, 1L);
        assertThat(portfolio.availableRooms()).isEqualTo(2);
        assertThat(portfolio.occupancyPercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void ownerUseReducesInventoryOnlyUntilItsExclusiveEndDate() {
        roomBlockRepository.findAll().forEach(block -> {
            block.setStatus(RoomBlockStatus.COMPLETED);
            roomBlockRepository.save(block);
        });
        roomBlock(room102, RoomBlockType.OWNER_USE, from, from.plusDays(1));

        PmsPerformanceReportResponse report = service.performance(
                company, property.getId(), from, from.plusDays(2));
        PmsPortfolioResponse portfolioAfterEnd = service.portfolio(company, from.plusDays(1));

        assertThat(report.availableRoomNights()).isEqualTo(3);
        assertThat(report.daily())
                .extracting(PmsPerformanceReportResponse.DailyPerformance::availableRooms)
                .containsExactly(1L, 2L);
        assertThat(portfolioAfterEnd.availableRooms()).isEqualTo(2);
    }

    private Room room(RoomType type, String number) {
        Room room = new Room();
        room.setProperty(property);
        room.setRoomType(type);
        room.setNumber(number);
        room.setActive(true);
        room.setOperationalStatus(RoomOperationalStatus.IN_SERVICE);
        room.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        return roomRepository.save(room);
    }

    private RoomBlock roomBlock(Room blockedRoom,
                                RoomBlockType type,
                                LocalDate startDate,
                                LocalDate endDate) {
        RoomBlock block = new RoomBlock();
        block.setProperty(property);
        block.setRoom(blockedRoom);
        block.setType(type);
        block.setStartDate(startDate);
        block.setEndDate(endDate);
        block.setReason("Wartung");
        block.setCreatedBy("Christopher");
        return roomBlockRepository.save(block);
    }
}
