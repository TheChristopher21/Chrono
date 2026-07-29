package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsPerformanceReportResponse;
import com.chrono.chrono.dto.pms.PmsPortfolioResponse;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.ReservationRepository;
import com.chrono.chrono.repositories.pms.RoomBlockRepository;
import com.chrono.chrono.repositories.pms.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PmsReportingService {

    private static final Set<ReservationStatus> SOLD_STATUSES = EnumSet.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN,
            ReservationStatus.CHECKED_OUT
    );
    private static final Set<RoomBlockType> INVENTORY_BLOCKING_ROOM_BLOCK_TYPES = EnumSet.of(
            RoomBlockType.OUT_OF_ORDER,
            RoomBlockType.OWNER_USE
    );

    private final HotelPropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final RoomBlockRepository roomBlockRepository;
    private final ReservationRepository reservationRepository;

    public PmsReportingService(HotelPropertyRepository propertyRepository,
                               RoomRepository roomRepository,
                               RoomBlockRepository roomBlockRepository,
                               ReservationRepository reservationRepository) {
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.roomBlockRepository = roomBlockRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public PmsPerformanceReportResponse performance(Company company,
                                                     Long propertyId,
                                                     LocalDate fromDate,
                                                     LocalDate toDateExclusive) {
        HotelProperty property = propertyRepository.findByIdAndCompany_Id(propertyId, company.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotelbetrieb nicht gefunden."));
        validatePeriod(fromDate, toDateExclusive);

        List<Room> rooms = roomRepository.findAllByProperty_IdOrderByFloorAscNumberAsc(propertyId).stream()
                .filter(Room::isActive)
                .filter(room -> room.getOperationalStatus() == RoomOperationalStatus.IN_SERVICE)
                .toList();
        Set<Long> operationalRoomIds = new HashSet<>(rooms.stream().map(Room::getId).toList());
        List<RoomBlock> blocks = roomBlockRepository
                .findAllByProperty_IdAndStartDateLessThanAndEndDateGreaterThanOrderByStartDateAsc(
                        propertyId, toDateExclusive, fromDate).stream()
                .filter(block -> block.getStatus() == RoomBlockStatus.ACTIVE)
                .filter(block -> INVENTORY_BLOCKING_ROOM_BLOCK_TYPES.contains(block.getType()))
                .toList();
        List<Reservation> reservations = reservationRepository
                .findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
                        propertyId, toDateExclusive, fromDate);

        List<PmsPerformanceReportResponse.DailyPerformance> daily = new ArrayList<>();
        EnumMap<ReservationSource, SourceAccumulator> sourceTotals = new EnumMap<>(ReservationSource.class);
        long availableRoomNights = 0;
        long soldRoomNights = 0;
        BigDecimal roomRevenue = BigDecimal.ZERO;

        for (LocalDate date = fromDate; date.isBefore(toDateExclusive); date = date.plusDays(1)) {
            LocalDate reportDate = date;
            long blockedRooms = blocks.stream()
                    .filter(block -> operationalRoomIds.contains(block.getRoom().getId()))
                    .filter(block -> overlaps(reportDate, block.getStartDate(), block.getEndDate()))
                    .map(block -> block.getRoom().getId())
                    .distinct()
                    .count();
            long available = Math.max(0, rooms.size() - blockedRooms);
            List<Reservation> sold = reservations.stream()
                    .filter(reservation -> SOLD_STATUSES.contains(reservation.getStatus()))
                    .filter(reservation -> overlaps(reportDate, reservation.getArrivalDate(), reservation.getDepartureDate()))
                    .toList();
            long soldRooms = sold.size();
            BigDecimal dailyRevenue = sold.stream()
                    .map(this::nightlyRevenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            for (Reservation reservation : sold) {
                SourceAccumulator source = sourceTotals.computeIfAbsent(
                        reservation.getSource(), ignored -> new SourceAccumulator());
                source.roomNights++;
                source.revenue = source.revenue.add(nightlyRevenue(reservation));
            }

            availableRoomNights += available;
            soldRoomNights += soldRooms;
            roomRevenue = roomRevenue.add(dailyRevenue);
            daily.add(new PmsPerformanceReportResponse.DailyPerformance(
                    reportDate, available, soldRooms, percent(soldRooms, available),
                    money(dailyRevenue), ratio(dailyRevenue, soldRooms), ratio(dailyRevenue, available)
            ));
        }

        long arrivals = reservations.stream()
                .filter(reservation -> SOLD_STATUSES.contains(reservation.getStatus()))
                .filter(reservation -> !reservation.getArrivalDate().isBefore(fromDate)
                        && reservation.getArrivalDate().isBefore(toDateExclusive))
                .count();
        long cancellations = reservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.CANCELLED)
                .count();
        long noShows = reservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.NO_SHOW)
                .count();
        long totalSoldRoomNights = soldRoomNights;
        List<PmsPerformanceReportResponse.SourcePerformance> sources = sourceTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new PmsPerformanceReportResponse.SourcePerformance(
                        entry.getKey(), entry.getValue().roomNights, money(entry.getValue().revenue),
                        percent(entry.getValue().roomNights, totalSoldRoomNights)))
                .toList();

        return new PmsPerformanceReportResponse(
                propertyId, property.getName(), property.getCurrencyCode(), fromDate, toDateExclusive,
                availableRoomNights, soldRoomNights, percent(soldRoomNights, availableRoomNights),
                money(roomRevenue), ratio(roomRevenue, soldRoomNights), ratio(roomRevenue, availableRoomNights),
                arrivals, cancellations, noShows,
                "Auswertung nach Aufenthaltsdatum; der Abreisetag zählt nicht als Übernachtung. "
                        + "Umsatz wird gleichmässig auf gebuchte Nächte verteilt; "
                        + "stornierte, nicht angereiste (No-Show), Angebots-, Options- und Wartelistenbuchungen zählen nicht als verkauft.",
                daily, sources
        );
    }

    @Transactional(readOnly = true)
    public PmsPortfolioResponse portfolio(Company company, LocalDate businessDate) {
        if (businessDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Betriebstag ist erforderlich.");
        }
        List<PmsPortfolioResponse.PropertySummary> hotels = propertyRepository
                .findAllByCompany_IdOrderByNameAsc(company.getId()).stream()
                .filter(HotelProperty::isActive)
                .map(property -> portfolioProperty(property, businessDate))
                .toList();
        long operationalRooms = hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::operationalRooms).sum();
        long availableRooms = hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::availableRooms).sum();
        long soldRooms = hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::soldRooms).sum();
        return new PmsPortfolioResponse(
                businessDate, hotels.size(), operationalRooms, availableRooms, soldRooms,
                percent(soldRooms, availableRooms),
                hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::arrivals).sum(),
                hotels.stream().mapToLong(PmsPortfolioResponse.PropertySummary::departures).sum(),
                hotels
        );
    }

    private PmsPortfolioResponse.PropertySummary portfolioProperty(HotelProperty property,
                                                                   LocalDate businessDate) {
        Long propertyId = property.getId();
        List<Room> rooms = roomRepository.findAllByProperty_IdOrderByFloorAscNumberAsc(propertyId).stream()
                .filter(Room::isActive)
                .filter(room -> room.getOperationalStatus() == RoomOperationalStatus.IN_SERVICE)
                .toList();
        Set<Long> roomIds = new HashSet<>(rooms.stream().map(Room::getId).toList());
        long blocked = roomBlockRepository
                .findAllByProperty_IdAndStartDateLessThanAndEndDateGreaterThanOrderByStartDateAsc(
                        propertyId, businessDate.plusDays(1), businessDate).stream()
                .filter(block -> block.getStatus() == RoomBlockStatus.ACTIVE)
                .filter(block -> INVENTORY_BLOCKING_ROOM_BLOCK_TYPES.contains(block.getType()))
                .filter(block -> roomIds.contains(block.getRoom().getId()))
                .map(block -> block.getRoom().getId())
                .distinct()
                .count();
        long available = Math.max(0, rooms.size() - blocked);
        List<Reservation> reservations = reservationRepository
                .findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
                        propertyId, businessDate.plusDays(1), businessDate);
        long sold = reservations.stream()
                .filter(reservation -> SOLD_STATUSES.contains(reservation.getStatus()))
                .filter(reservation -> overlaps(
                        businessDate, reservation.getArrivalDate(), reservation.getDepartureDate()))
                .count();
        long arrivals = reservationRepository.countByProperty_IdAndArrivalDateAndStatusIn(
                propertyId, businessDate, SOLD_STATUSES);
        long departures = reservationRepository.countByProperty_IdAndDepartureDateAndStatusIn(
                propertyId, businessDate, SOLD_STATUSES);
        return new PmsPortfolioResponse.PropertySummary(
                propertyId, property.getCode(), property.getName(), property.getCity(),
                property.getTimezone(), property.getCurrencyCode(), rooms.size(), available,
                sold, percent(sold, available), arrivals, departures);
    }

    private void validatePeriod(LocalDate fromDate, LocalDate toDateExclusive) {
        if (fromDate == null || toDateExclusive == null || !toDateExclusive.isAfter(fromDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Berichtszeitraum muss mindestens einen Tag umfassen.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDateExclusive) > 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Der Berichtszeitraum darf höchstens 366 Tage umfassen.");
        }
    }

    private boolean overlaps(LocalDate date, LocalDate start, LocalDate endExclusive) {
        return !date.isBefore(start) && date.isBefore(endExclusive);
    }

    private BigDecimal nightlyRevenue(Reservation reservation) {
        long nights = Math.max(1, ChronoUnit.DAYS.between(
                reservation.getArrivalDate(), reservation.getDepartureDate()));
        return reservation.getTotalAmount().divide(BigDecimal.valueOf(nights), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO.setScale(2);
        return numerator.divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static class SourceAccumulator {
        private long roomNights;
        private BigDecimal revenue = BigDecimal.ZERO;
    }
}
