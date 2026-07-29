package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PmsOperationsService {

    private static final Set<ReservationStatus> NON_INVENTORY_STATUSES =
            Set.of(
                    ReservationStatus.OFFERED,
                    ReservationStatus.WAITLISTED,
                    ReservationStatus.CANCELLED,
                    ReservationStatus.NO_SHOW,
                    ReservationStatus.CHECKED_OUT
            );
    private static final Set<ReservationStatus> OPERATIONAL_ARRIVAL_STATUSES =
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);
    private static final Set<ReservationStatus> OPERATIONAL_DEPARTURE_STATUSES =
            Set.of(ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT);
    private static final Set<ReservationStatus> SOLD_ROOM_STATUSES =
            Set.of(
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.CHECKED_IN,
                    ReservationStatus.CHECKED_OUT
            );
    private static final Set<ReservationStatus> HOLD_STATUSES =
            Set.of(ReservationStatus.OFFERED, ReservationStatus.TENTATIVE);
    private static final Set<RoomBlockType> INVENTORY_BLOCKING_ROOM_BLOCK_TYPES =
            Set.of(RoomBlockType.OUT_OF_ORDER, RoomBlockType.OWNER_USE);

    private final HotelPropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final GuestProfileRepository guestRepository;
    private final RatePlanRepository ratePlanRepository;
    private final RateOverrideRepository rateOverrideRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    private final FolioRepository folioRepository;
    private final FolioItemRepository folioItemRepository;
    private final PaymentRepository paymentRepository;
    private final CashShiftRepository cashShiftRepository;
    private final RoomBlockRepository roomBlockRepository;
    private final MaintenanceWorkOrderRepository maintenanceWorkOrderRepository;
    private final HousekeepingTaskRepository housekeepingTaskRepository;
    private final IntegrationOutboxRepository outboxRepository;
    private final PmsAuditWriter auditWriter;
    private final List<PmsPaymentGateway> paymentGateways;

    public PmsOperationsService(HotelPropertyRepository propertyRepository,
                                RoomTypeRepository roomTypeRepository,
                                RoomRepository roomRepository,
                                GuestProfileRepository guestRepository,
                                RatePlanRepository ratePlanRepository,
                                RateOverrideRepository rateOverrideRepository,
                                ReservationRepository reservationRepository,
                                ReservationStatusHistoryRepository reservationStatusHistoryRepository,
                                FolioRepository folioRepository,
                                FolioItemRepository folioItemRepository,
                                PaymentRepository paymentRepository,
                                CashShiftRepository cashShiftRepository,
                                RoomBlockRepository roomBlockRepository,
                                MaintenanceWorkOrderRepository maintenanceWorkOrderRepository,
                                HousekeepingTaskRepository housekeepingTaskRepository,
                                IntegrationOutboxRepository outboxRepository,
                                PmsAuditWriter auditWriter,
                                List<PmsPaymentGateway> paymentGateways) {
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.guestRepository = guestRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.rateOverrideRepository = rateOverrideRepository;
        this.reservationRepository = reservationRepository;
        this.reservationStatusHistoryRepository = reservationStatusHistoryRepository;
        this.folioRepository = folioRepository;
        this.folioItemRepository = folioItemRepository;
        this.paymentRepository = paymentRepository;
        this.cashShiftRepository = cashShiftRepository;
        this.roomBlockRepository = roomBlockRepository;
        this.maintenanceWorkOrderRepository = maintenanceWorkOrderRepository;
        this.housekeepingTaskRepository = housekeepingTaskRepository;
        this.outboxRepository = outboxRepository;
        this.auditWriter = auditWriter;
        this.paymentGateways = List.copyOf(paymentGateways);
    }

    @Transactional(readOnly = true)
    public PmsOperationsResponse getOperations(Company company,
                                               Long propertyId,
                                               LocalDate businessDate,
                                               LocalDate from,
                                               LocalDate to) {
        HotelProperty property = requireProperty(company, propertyId);
        LocalDate safeDate = businessDate == null ? today(property) : businessDate;
        LocalDate rangeStart = from == null ? safeDate.minusDays(7) : from;
        LocalDate rangeEnd = to == null ? safeDate.plusDays(31) : to;
        validateRange(rangeStart, rangeEnd);

        List<Reservation> reservations = reservationRepository
                .findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
                        propertyId,
                        rangeEnd,
                        rangeStart
                );
        List<PmsOperationsResponse.ReservationView> reservationViews = reservations.stream()
                .map(this::toReservationView)
                .toList();
        List<PmsOperationsResponse.ReservationView> arrivals = reservationViews.stream()
                .filter(view -> view.arrivalDate().equals(safeDate))
                .filter(view -> OPERATIONAL_ARRIVAL_STATUSES.contains(view.status()))
                .toList();
        List<PmsOperationsResponse.ReservationView> departures = reservationViews.stream()
                .filter(view -> view.departureDate().equals(safeDate))
                .filter(view -> OPERATIONAL_DEPARTURE_STATUSES.contains(view.status()))
                .toList();

        List<GuestProfile> guests = guestRepository.findAllByCompany_IdOrderByLastNameAscFirstNameAsc(company.getId());
        List<RatePlan> ratePlans = ratePlanRepository.findAllByProperty_IdOrderByRoomType_SortOrderAscNameAsc(propertyId);
        List<RateOverride> rateOverrides =
                rateOverrideRepository.findAllByRatePlan_Property_IdAndStayDateBetweenOrderByStayDateAsc(
                        propertyId,
                        rangeStart,
                        rangeEnd
                );
        List<Room> rooms = roomRepository.findAllByProperty_IdOrderByFloorAscNumberAsc(propertyId);
        List<Folio> folios = folioRepository.findAllByReservation_Property_IdOrderByCreatedAtDesc(propertyId);
        List<PmsOperationsResponse.FolioView> folioViews = folios.stream().map(this::toFolioView).toList();
        List<RoomBlock> roomBlocks = roomBlockRepository
                .findAllByProperty_IdAndStartDateLessThanAndEndDateGreaterThanOrderByStartDateAsc(
                        propertyId, rangeEnd, rangeStart);
        Set<Long> inventoryBlockedRoomIds = roomBlocks.stream()
                .filter(block -> block.getStatus() == RoomBlockStatus.ACTIVE)
                .filter(block -> INVENTORY_BLOCKING_ROOM_BLOCK_TYPES.contains(block.getType()))
                .filter(block -> block.getStartDate().isBefore(safeDate.plusDays(1))
                        && block.getEndDate().isAfter(safeDate))
                .map(block -> block.getRoom().getId())
                .collect(Collectors.toSet());

        long soldRooms = reservations.stream()
                .filter(reservation -> SOLD_ROOM_STATUSES.contains(reservation.getStatus()))
                .filter(reservation -> !safeDate.isBefore(reservation.getArrivalDate())
                        && safeDate.isBefore(reservation.getDepartureDate()))
                .count();
        long totalRooms = rooms.stream()
                .filter(Room::isActive)
                .filter(room -> room.getOperationalStatus() == RoomOperationalStatus.IN_SERVICE)
                .filter(room -> !inventoryBlockedRoomIds.contains(room.getId()))
                .count();
        long dirtyRooms = rooms.stream()
                .filter(Room::isActive)
                .filter(room -> room.getOperationalStatus() == RoomOperationalStatus.IN_SERVICE)
                .filter(room -> !inventoryBlockedRoomIds.contains(room.getId()))
                .filter(room -> room.getHousekeepingStatus() == HousekeepingStatus.DIRTY)
                .count();
        long openFolios = folioViews.stream().filter(folio -> folio.status() == FolioStatus.OPEN).count();
        BigDecimal openBalance = folioViews.stream()
                .filter(folio -> folio.status() == FolioStatus.OPEN)
                .map(PmsOperationsResponse.FolioView::balance)
                .filter(balance -> balance.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int occupancyPercent = totalRooms == 0
                ? 0
                : (int) Math.round((soldRooms * 100.0) / totalRooms);

        Map<Long, Reservation> currentByRoom = reservations.stream()
                .filter(reservation -> reservation.getRoom() != null)
                .filter(reservation -> !NON_INVENTORY_STATUSES.contains(reservation.getStatus()))
                .filter(reservation -> !safeDate.isBefore(reservation.getArrivalDate())
                        && safeDate.isBefore(reservation.getDepartureDate()))
                .collect(Collectors.toMap(
                        reservation -> reservation.getRoom().getId(),
                        Function.identity(),
                        (left, right) -> left
                ));

        PmsOperationsResponse.MetricsView metrics = new PmsOperationsResponse.MetricsView(
                totalRooms,
                soldRooms,
                Math.max(0, totalRooms - soldRooms),
                occupancyPercent,
                reservationRepository.countByProperty_IdAndStatus(propertyId, ReservationStatus.CHECKED_IN),
                arrivals.size(),
                departures.size(),
                dirtyRooms,
                openFolios,
                money(openBalance)
        );

        return new PmsOperationsResponse(
                propertyId,
                property.getName(),
                property.getCurrencyCode(),
                safeDate,
                metrics,
                reservationViews,
                arrivals,
                departures,
                guests.stream().map(this::toGuestView).toList(),
                ratePlans.stream().map(this::toRatePlanView).toList(),
                rateOverrides.stream().map(this::toRateOverrideView).toList(),
                rooms.stream().map(room -> toRoomStateView(room, currentByRoom.get(room.getId()))).toList(),
                housekeepingTaskRepository
                        .findAllByProperty_IdAndServiceDateOrderByPriorityDescRoom_NumberAsc(propertyId, safeDate)
                        .stream()
                        .map(this::toHousekeepingView)
                        .toList(),
                folioViews,
                cashShiftRepository
                        .findFirstByProperty_IdAndStatusOrderByOpenedAtDesc(propertyId, CashShiftStatus.OPEN)
                        .map(this::toCashShiftView)
                        .orElse(null),
                roomBlocks.stream()
                        .map(this::toRoomBlockView)
                        .toList(),
                maintenanceWorkOrderRepository.findAllByProperty_IdOrderByReportedAtDesc(propertyId)
                        .stream()
                        .map(this::toMaintenanceView)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Company company,
                                                Long propertyId,
                                                LocalDate arrival,
                                                LocalDate departure) {
        HotelProperty property = requireProperty(company, propertyId);
        validateStay(arrival, departure);
        List<RoomType> roomTypes = roomTypeRepository.findAllByProperty_IdOrderBySortOrderAscNameAsc(propertyId);
        List<RatePlan> ratePlans = ratePlanRepository.findAllByProperty_IdOrderByRoomType_SortOrderAscNameAsc(propertyId);

        List<AvailabilityResponse.RoomTypeAvailability> availability = roomTypes.stream()
                .filter(RoomType::isActive)
                .map(roomType -> {
                    long total = countSellableRooms(propertyId, roomType.getId());
                    long sold = maximumSold(propertyId, roomType.getId(), arrival, departure, null);
                    long available = minimumAvailable(propertyId, roomType.getId(), arrival, departure, null);
                    List<AvailabilityResponse.RateOption> rates = ratePlans.stream()
                            .filter(RatePlan::isActive)
                            .filter(ratePlan -> ratePlan.getRoomType().getId().equals(roomType.getId()))
                            .map(ratePlan -> {
                                Quote quote = quote(ratePlan, arrival, departure);
                                boolean canBook = available > 0 && quote.restriction() == null;
                                return new AvailabilityResponse.RateOption(
                                        ratePlan.getId(),
                                        ratePlan.getCode(),
                                        ratePlan.getName(),
                                        ratePlan.getCurrencyCode(),
                                        quote.total(),
                                        canBook,
                                        available == 0 ? "Ausgebucht" : quote.restriction()
                                );
                            })
                            .toList();
                    return new AvailabilityResponse.RoomTypeAvailability(
                            roomType.getId(),
                            roomType.getCode(),
                            roomType.getName(),
                            total,
                            sold,
                            available,
                            rates
                    );
                })
                .toList();

        return new AvailabilityResponse(property.getId(), arrival, departure, availability);
    }

    @Transactional
    public PmsOperationsResponse createGuest(Company company,
                                             Long propertyId,
                                             UpsertGuestRequest request,
                                             LocalDate businessDate) {
        requireProperty(company, propertyId);
        GuestProfile guest = new GuestProfile();
        guest.setCompany(company);
        applyGuest(guest, request);
        guestRepository.save(guest);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse updateGuest(Company company,
                                             Long propertyId,
                                             Long guestId,
                                             UpsertGuestRequest request,
                                             LocalDate businessDate) {
        requireProperty(company, propertyId);
        GuestProfile guest = guestRepository.findByIdAndCompany_Id(guestId, company.getId())
                .orElseThrow(() -> notFound("Gast nicht gefunden."));
        applyGuest(guest, request);
        guestRepository.save(guest);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse createRatePlan(Company company,
                                                Long propertyId,
                                                UpsertRatePlanRequest request,
                                                LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        RoomType roomType = requireRoomType(company, propertyId, request.roomTypeId());
        String code = code(request.code());
        if (ratePlanRepository.existsByProperty_IdAndCodeIgnoreCase(propertyId, code)) {
            throw conflict("Ein Ratenplan mit diesem Code existiert bereits.");
        }
        RatePlan ratePlan = new RatePlan();
        ratePlan.setProperty(property);
        ratePlan.setRoomType(roomType);
        applyRatePlan(ratePlan, request, property, true);
        ratePlanRepository.save(ratePlan);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse updateRatePlan(Company company,
                                                Long propertyId,
                                                Long ratePlanId,
                                                UpsertRatePlanRequest request,
                                                LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        RatePlan ratePlan = ratePlanRepository.findByIdAndProperty_Company_Id(ratePlanId, company.getId())
                .orElseThrow(() -> notFound("Ratenplan nicht gefunden."));
        if (!ratePlan.getProperty().getId().equals(propertyId)) {
            throw notFound("Ratenplan nicht gefunden.");
        }
        if (ratePlanRepository.existsByProperty_IdAndCodeIgnoreCaseAndIdNot(
                propertyId,
                code(request.code()),
                ratePlanId
        )) {
            throw conflict("Ein Ratenplan mit diesem Code existiert bereits.");
        }
        ratePlan.setRoomType(requireRoomType(company, propertyId, request.roomTypeId()));
        applyRatePlan(ratePlan, request, property, false);
        ratePlanRepository.save(ratePlan);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse upsertRateOverride(Company company,
                                                   Long propertyId,
                                                   Long ratePlanId,
                                                   UpsertRateOverrideRequest request,
                                                   LocalDate businessDate) {
        requireProperty(company, propertyId);
        RatePlan ratePlan = ratePlanRepository.findByIdAndProperty_Company_Id(ratePlanId, company.getId())
                .orElseThrow(() -> notFound("Ratenplan nicht gefunden."));
        if (!ratePlan.getProperty().getId().equals(propertyId)) {
            throw notFound("Ratenplan nicht gefunden.");
        }
        RateOverride override = rateOverrideRepository
                .findByRatePlan_IdAndStayDate(ratePlanId, request.stayDate())
                .orElseGet(RateOverride::new);
        override.setRatePlan(ratePlan);
        override.setStayDate(request.stayDate());
        override.setPrice(money(request.price()));
        override.setMinStay(request.minStay());
        override.setClosed(request.closed());
        override.setClosedArrival(request.closedArrival());
        override.setClosedDeparture(request.closedDeparture());
        rateOverrideRepository.save(override);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse createReservation(Company company,
                                                   UpsertReservationRequest request,
                                                   String username,
                                                   LocalDate businessDate) {
        Reservation reservation = createReservationRecord(company, request, username);
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    Reservation createReservationRecord(Company company,
                                        UpsertReservationRequest request,
                                        String username) {
        HotelProperty property = lockProperty(company, request.propertyId());
        Reservation reservation = new Reservation();
        reservation.setProperty(property);
        reservation.setConfirmationCode(generateConfirmationCode());
        reservation.setCreatedBy(clean(username) == null ? "system" : clean(username));
        applyReservation(company, reservation, request, null);
        reservationRepository.save(reservation);
        recordHistory(reservation, null, reservation.getStatus(), reservation.getCreatedBy(), "Reservierung angelegt");
        createFolioWithRoomCharges(reservation);
        emit(reservation, "reservation.created");
        return reservation;
    }

    @Transactional
    public PmsOperationsResponse updateReservation(Company company,
                                                   Long reservationId,
                                                   UpsertReservationRequest request,
                                                   String username,
                                                   LocalDate businessDate) {
        HotelProperty property = lockProperty(company, request.propertyId());
        Reservation reservation = reservationRepository.findByIdAndProperty_Company_Id(reservationId, company.getId())
                .orElseThrow(() -> notFound("Reservierung nicht gefunden."));
        if (!reservation.getProperty().getId().equals(property.getId())) {
            throw badRequest("Eine Reservierung kann nicht in ein anderes Hotel verschoben werden.");
        }
        if (reservation.getStatus() == ReservationStatus.CHECKED_OUT
                || reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.NO_SHOW) {
            throw conflict("Abgeschlossene oder stornierte Reservierungen können nicht geändert werden.");
        }
        ReservationStatus previousStatus = reservation.getStatus();
        applyReservation(company, reservation, request, reservationId);
        reservationRepository.save(reservation);
        if (previousStatus != reservation.getStatus()) {
            recordHistory(reservation, previousStatus, reservation.getStatus(), username, "Status bearbeitet");
        }
        refreshRoomCharges(reservation);
        emit(reservation, "reservation.updated");
        return getOperations(company, property.getId(), businessDate, null, null);
    }

    public PmsOperationsResponse updateReservation(Company company,
                                                   Long reservationId,
                                                   UpsertReservationRequest request,
                                                   LocalDate businessDate) {
        return updateReservation(company, reservationId, request, "system", businessDate);
    }

    @Transactional
    public PmsOperationsResponse checkIn(Company company,
                                         Long reservationId,
                                         String username,
                                         LocalDate businessDate) {
        Reservation reservation = requireReservation(company, reservationId);
        HotelProperty property = lockProperty(company, reservation.getProperty().getId());
        LocalDate now = today(property);
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw conflict("Nur bestätigte Reservierungen können eingecheckt werden.");
        }
        if (now.isBefore(reservation.getArrivalDate()) || !now.isBefore(reservation.getDepartureDate())) {
            throw conflict("Der Check-in liegt ausserhalb des gebuchten Aufenthalts.");
        }
        Room room = reservation.getRoom();
        if (room == null) {
            throw conflict("Vor dem Check-in muss ein Zimmer zugewiesen werden.");
        }
        ensureRoomReady(room);
        if (reservationRepository.countOverlappingByRoom(
                room.getId(),
                reservation.getArrivalDate(),
                reservation.getDepartureDate(),
                NON_INVENTORY_STATUSES,
                reservation.getId()
        ) > 0) {
            throw conflict("Das zugewiesene Zimmer ist bereits belegt.");
        }
        transition(reservation, ReservationStatus.CHECKED_IN, username, "Check-in");
        reservation.setCheckedInAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        emit(reservation, "reservation.checked_in");
        return getOperations(company, property.getId(), businessDate, null, null);
    }

    public PmsOperationsResponse checkIn(Company company,
                                         Long reservationId,
                                         LocalDate businessDate) {
        return checkIn(company, reservationId, "system", businessDate);
    }

    @Transactional
    public PmsOperationsResponse checkOut(Company company,
                                          Long reservationId,
                                          String username,
                                          LocalDate businessDate) {
        Reservation reservation = requireReservation(company, reservationId);
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN) {
            throw conflict("Nur eingecheckte Aufenthalte können ausgecheckt werden.");
        }
        List<Folio> folios = folioRepository.findAllByReservation_IdOrderByIdAsc(reservationId);
        if (folios.isEmpty()) {
            throw conflict("Zur Reservierung fehlt das Gastkonto.");
        }
        boolean unbalanced = folios.stream()
                .map(this::toFolioView)
                .anyMatch(view -> view.balance().abs().compareTo(new BigDecimal("0.01")) >= 0);
        if (unbalanced) {
            throw conflict("Vor dem Check-out muss das Gastkonto vollständig ausgeglichen werden.");
        }
        transition(reservation, ReservationStatus.CHECKED_OUT, username, "Check-out");
        reservation.setCheckedOutAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        folios.forEach(folio -> {
            folio.setStatus(FolioStatus.CLOSED);
            folio.setClosedAt(LocalDateTime.now());
            folioRepository.save(folio);
        });
        if (reservation.getRoom() != null) {
            markRoomDirty(reservation.getProperty(), reservation.getRoom(), today(reservation.getProperty()));
        }
        emit(reservation, "reservation.checked_out");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    public PmsOperationsResponse checkOut(Company company,
                                          Long reservationId,
                                          LocalDate businessDate) {
        return checkOut(company, reservationId, "system", businessDate);
    }

    @Transactional
    public PmsOperationsResponse cancelReservation(Company company,
                                                   Long reservationId,
                                                   ReservationLifecycleRequest request,
                                                   String username,
                                                   LocalDate businessDate) {
        Reservation reservation = requireReservation(company, reservationId);
        if (reservation.getStatus() != ReservationStatus.OFFERED
                && reservation.getStatus() != ReservationStatus.TENTATIVE
                && reservation.getStatus() != ReservationStatus.WAITLISTED
                && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw conflict("Nur offene Reservierungen können storniert werden.");
        }
        List<Folio> folios = folioRepository.findAllByReservation_IdOrderByIdAsc(reservationId);
        boolean hasPayments = folios.stream().anyMatch(folio ->
                paymentRepository.existsByFolio_IdAndStatusAndAmountGreaterThan(
                        folio.getId(),
                        PaymentStatus.POSTED,
                        BigDecimal.ZERO
                ));
        if (hasPayments) {
            throw conflict("Vor der Stornierung müssen bestehende Zahlungen rückerstattet oder storniert werden.");
        }
        String reason = request == null ? null : clean(request.reason());
        transition(reservation, ReservationStatus.CANCELLED, username,
                reason == null ? "Stornierung" : reason);
        reservation.setCancellationReason(reason);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setHoldUntil(null);
        reservationRepository.save(reservation);
        folios.forEach(folio -> {
            folio.setStatus(FolioStatus.CLOSED);
            folio.setClosedAt(LocalDateTime.now());
            folioRepository.save(folio);
        });
        emit(reservation, "reservation.cancelled");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    public PmsOperationsResponse cancelReservation(Company company,
                                                   Long reservationId,
                                                   LocalDate businessDate) {
        return cancelReservation(company, reservationId, null, "system", businessDate);
    }

    @Transactional
    public PmsOperationsResponse markNoShow(Company company,
                                            Long reservationId,
                                            String username,
                                            LocalDate businessDate) {
        Reservation reservation = requireReservation(company, reservationId);
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw conflict("Nur bestätigte Reservierungen können als nicht angereist (No-Show) markiert werden.");
        }
        if (today(reservation.getProperty()).isBefore(reservation.getArrivalDate())) {
            throw conflict("Eine Reservierung kann nicht vor dem Anreisetag als nicht angereist (No-Show) markiert werden.");
        }
        transition(reservation, ReservationStatus.NO_SHOW, username, "Nicht angereist (No-Show)");
        reservation.setNoShowAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        emit(reservation, "reservation.no_show");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    public PmsOperationsResponse markNoShow(Company company,
                                            Long reservationId,
                                            LocalDate businessDate) {
        return markNoShow(company, reservationId, "system", businessDate);
    }

    @Transactional
    public PmsOperationsResponse confirmReservation(Company company,
                                                    Long reservationId,
                                                    ReservationLifecycleRequest request,
                                                    String username,
                                                    LocalDate businessDate) {
        Reservation reservation = requireReservation(company, reservationId);
        if (reservation.getStatus() != ReservationStatus.OFFERED
                && reservation.getStatus() != ReservationStatus.TENTATIVE
                && reservation.getStatus() != ReservationStatus.WAITLISTED) {
            throw conflict("Nur Angebote, Optionen oder Wartelisten-Einträge können bestätigt werden.");
        }
        lockProperty(company, reservation.getProperty().getId());
        ensureCapacity(
                reservation.getProperty().getId(),
                reservation.getRoomType().getId(),
                reservation.getArrivalDate(),
                reservation.getDepartureDate(),
                reservation.getId()
        );
        ensureAssignedRoomAvailable(reservation, reservation.getRoom(), reservation.getId());
        reservation.setGuaranteeStatus(request == null || request.guaranteeStatus() == null
                ? reservation.getGuaranteeStatus()
                : request.guaranteeStatus());
        reservation.setHoldUntil(null);
        transition(reservation, ReservationStatus.CONFIRMED, username,
                request == null ? "Buchung bestätigt" : request.reason());
        reservationRepository.save(reservation);
        emit(reservation, "reservation.confirmed");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse changeLifecycleStatus(Company company,
                                                       Long reservationId,
                                                       ReservationStatus targetStatus,
                                                       ReservationLifecycleRequest request,
                                                       String username,
                                                       LocalDate businessDate) {
        if (targetStatus != ReservationStatus.OFFERED
                && targetStatus != ReservationStatus.TENTATIVE
                && targetStatus != ReservationStatus.WAITLISTED) {
            throw badRequest("Der angeforderte Reservierungsstatus wird hier nicht unterstützt.");
        }
        Reservation reservation = requireReservation(company, reservationId);
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN
                || reservation.getStatus() == ReservationStatus.CHECKED_OUT
                || reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.NO_SHOW) {
            throw conflict("Der Status dieser Reservierung kann nicht mehr geändert werden.");
        }
        LocalDateTime holdUntil = request == null ? null : request.holdUntil();
        if (HOLD_STATUSES.contains(targetStatus)) {
            if (holdUntil == null) {
                holdUntil = LocalDateTime.now().plusHours(targetStatus == ReservationStatus.OFFERED ? 24 : 48);
            }
            if (!holdUntil.isAfter(LocalDateTime.now())) {
                throw badRequest("Die Haltefrist muss in der Zukunft liegen.");
            }
        } else {
            holdUntil = null;
        }
        reservation.setHoldUntil(holdUntil);
        if (request != null && request.guaranteeStatus() != null) {
            reservation.setGuaranteeStatus(request.guaranteeStatus());
        }
        transition(reservation, targetStatus, username, request == null ? null : request.reason());
        reservationRepository.save(reservation);
        emit(reservation, "reservation.status_changed");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse moveReservationRoom(Company company,
                                                     Long reservationId,
                                                     MoveReservationRoomRequest request,
                                                     String username,
                                                     LocalDate businessDate) {
        Reservation reservation = requireReservation(company, reservationId);
        if (reservation.getStatus() == ReservationStatus.CHECKED_OUT
                || reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.NO_SHOW) {
            throw conflict("Bei abgeschlossenen Reservierungen ist kein Zimmerwechsel möglich.");
        }
        Room target = roomRepository.findByIdAndProperty_Company_Id(request.roomId(), company.getId())
                .orElseThrow(() -> notFound("Zimmer nicht gefunden."));
        if (!target.getProperty().getId().equals(reservation.getProperty().getId())
                || !target.getRoomType().getId().equals(reservation.getRoomType().getId())) {
            throw badRequest("Das Zielzimmer muss zum Hotel und Zimmertyp der Reservierung passen.");
        }
        ensureAssignedRoomAvailable(reservation, target, reservation.getId());
        Room previous = reservation.getRoom();
        if (previous != null && previous.getId().equals(target.getId())) {
            return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
        }
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            ensureRoomReady(target);
            if (previous != null) {
                markRoomDirty(reservation.getProperty(), previous, today(reservation.getProperty()));
            }
        }
        reservation.setRoom(target);
        reservationRepository.save(reservation);
        String reason = clean(request.reason());
        String description = "Zimmerwechsel "
                + (previous == null ? "ohne Zuweisung" : previous.getNumber())
                + " → " + target.getNumber()
                + (reason == null ? "" : ": " + reason);
        recordHistory(reservation, reservation.getStatus(), reservation.getStatus(), username, description);
        emit(reservation, "reservation.room_moved");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    @Transactional
    public int expireReservationHolds(LocalDateTime now) {
        List<Reservation> expired = reservationRepository.findAllByStatusInAndHoldUntilBefore(HOLD_STATUSES, now);
        for (Reservation reservation : expired) {
            transition(reservation, ReservationStatus.CANCELLED, "system", "Haltefrist abgelaufen");
            reservation.setCancelledAt(now);
            reservation.setCancellationReason("Haltefrist abgelaufen");
            reservation.setHoldUntil(null);
            reservationRepository.save(reservation);
            folioRepository.findAllByReservation_IdOrderByIdAsc(reservation.getId()).forEach(folio -> {
                folio.setStatus(FolioStatus.CLOSED);
                folio.setClosedAt(now);
                folioRepository.save(folio);
            });
            emit(reservation, "reservation.hold_expired");
        }
        return expired.size();
    }

    @Transactional
    public PmsOperationsResponse postFolioItem(Company company,
                                               Long propertyId,
                                               Long folioId,
                                               PostFolioItemRequest request,
                                               LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        Folio folio = requireOpenFolio(company, propertyId, folioId);
        FolioItem item = new FolioItem();
        item.setFolio(folio);
        item.setServiceDate(request.serviceDate());
        item.setType(request.type());
        item.setDescription(required(request.description()));
        item.setQuantity(request.quantity().setScale(2, RoundingMode.HALF_UP));
        item.setUnitPrice(money(request.unitPrice()));
        item.setTotalAmount(money(request.quantity().multiply(request.unitPrice())));
        folioItemRepository.save(item);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse postPayment(Company company,
                                             Long propertyId,
                                             Long folioId,
                                             PostPaymentRequest request,
                                             String username,
                                             LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        Folio folio = requireOpenFolio(company, propertyId, folioId);
        BigDecimal balance = toFolioView(folio).balance();
        if (request.amount().compareTo(balance) > 0) {
            throw badRequest("Die Zahlung darf den offenen Betrag nicht überschreiten.");
        }
        String paymentReference = clean(request.reference());
        if (request.method() == PaymentMethod.CARD) {
            try {
                paymentReference = gatewayFor(PaymentMethod.CARD).verifyCapturedPayment(
                        property, folio, money(request.amount()), paymentReference);
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (Exception exception) {
                throw badGateway("Die Kartenzahlung konnte beim Zahlungsprovider nicht bestätigt werden.");
            }
        }
        Payment payment = new Payment();
        payment.setFolio(folio);
        payment.setAmount(money(request.amount()));
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.POSTED);
        payment.setKind(PaymentKind.PAYMENT);
        payment.setReference(paymentReference);
        payment.setCreatedBy(clean(username) == null ? "system" : clean(username));
        if (request.method() == PaymentMethod.CASH) {
            cashShiftRepository
                    .findFirstByProperty_IdAndStatusOrderByOpenedAtDesc(propertyId, CashShiftStatus.OPEN)
                    .orElseThrow(() -> conflict("Vor einer Barzahlung muss eine Kassenschicht geöffnet werden."));
        }
        paymentRepository.save(payment);
        emit(folio.getReservation(), "payment.posted");
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse refundPayment(Company company,
                                               Long propertyId,
                                               Long paymentId,
                                               RefundPaymentRequest request,
                                               String username,
                                               LocalDate businessDate) {
        requireProperty(company, propertyId);
        Payment original = paymentRepository.findById(paymentId)
                .filter(payment -> payment.getFolio().getReservation().getProperty().getCompany().getId()
                        .equals(company.getId()))
                .orElseThrow(() -> notFound("Zahlung nicht gefunden."));
        if (!original.getFolio().getReservation().getProperty().getId().equals(propertyId)) {
            throw notFound("Zahlung nicht gefunden.");
        }
        if (original.getStatus() != PaymentStatus.POSTED || original.getKind() != PaymentKind.PAYMENT) {
            throw conflict("Nur gebuchte Originalzahlungen können rückerstattet werden.");
        }
        if (original.getFolio().getStatus() != FolioStatus.OPEN) {
            throw conflict("Rückerstattungen benötigen ein offenes Gastkonto.");
        }
        BigDecimal refunded = paymentRepository
                .findAllByOriginalPayment_IdAndStatus(original.getId(), PaymentStatus.POSTED)
                .stream()
                .map(Payment::getAmount)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = money(request.amount());
        if (refunded.add(amount).compareTo(original.getAmount()) > 0) {
            throw badRequest("Die Rückerstattung überschreitet den noch erstattbaren Betrag.");
        }
        if (original.getMethod() == PaymentMethod.CASH) {
            cashShiftRepository
                    .findFirstByProperty_IdAndStatusOrderByOpenedAtDesc(propertyId, CashShiftStatus.OPEN)
                    .orElseThrow(() -> conflict("Für eine Bar-Rückerstattung muss eine Kassenschicht geöffnet sein."));
        }
        if (original.getMethod() == PaymentMethod.CARD) {
            try {
                gatewayFor(PaymentMethod.CARD).refund(
                        original,
                        amount,
                        clean(request.reason()),
                        "chrono-pms-refund-" + original.getId() + "-" + amount.toPlainString());
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (Exception exception) {
                throw badGateway("Die Kartenrückerstattung ist beim Zahlungsprovider fehlgeschlagen.");
            }
        }
        Payment refund = new Payment();
        refund.setFolio(original.getFolio());
        refund.setOriginalPayment(original);
        refund.setAmount(amount.negate());
        refund.setMethod(original.getMethod());
        refund.setStatus(PaymentStatus.POSTED);
        refund.setKind(PaymentKind.REFUND);
        refund.setReference(original.getReference());
        refund.setReason(clean(request.reason()));
        refund.setCreatedBy(clean(username) == null ? "system" : clean(username));
        paymentRepository.save(refund);
        emit(original.getFolio().getReservation(), "payment.refunded");
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse voidPayment(Company company,
                                             Long propertyId,
                                             Long paymentId,
                                             VoidPaymentRequest request,
                                             String username,
                                             LocalDate businessDate) {
        requireProperty(company, propertyId);
        Payment payment = paymentRepository.findById(paymentId)
                .filter(entry -> entry.getFolio().getReservation().getProperty().getCompany().getId()
                        .equals(company.getId()))
                .orElseThrow(() -> notFound("Zahlung nicht gefunden."));
        if (!payment.getFolio().getReservation().getProperty().getId().equals(propertyId)) {
            throw notFound("Zahlung nicht gefunden.");
        }
        if (payment.getStatus() != PaymentStatus.POSTED) {
            throw conflict("Die Zahlung wurde bereits storniert.");
        }
        if (payment.getKind() == PaymentKind.PAYMENT
                && !paymentRepository.findAllByOriginalPayment_IdAndStatus(paymentId, PaymentStatus.POSTED).isEmpty()) {
            throw conflict("Eine teilweise oder vollständig rückerstattete Zahlung kann nicht storniert werden.");
        }
        if (payment.getMethod() == PaymentMethod.CARD) {
            try {
                gatewayFor(PaymentMethod.CARD).voidPayment(
                        payment,
                        clean(request.reason()),
                        "chrono-pms-void-" + payment.getId());
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (Exception exception) {
                throw badGateway("Die Kartenstornierung ist beim Zahlungsprovider fehlgeschlagen.");
            }
        }
        payment.setStatus(PaymentStatus.VOIDED);
        payment.setReason(clean(request.reason()));
        payment.setVoidedAt(LocalDateTime.now());
        payment.setVoidedBy(clean(username) == null ? "system" : clean(username));
        paymentRepository.save(payment);
        emit(payment.getFolio().getReservation(), "payment.voided");
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse openCashShift(Company company,
                                               Long propertyId,
                                               OpenCashShiftRequest request,
                                               String username,
                                               LocalDate businessDate) {
        HotelProperty property = lockProperty(company, propertyId);
        if (cashShiftRepository
                .findFirstByProperty_IdAndStatusOrderByOpenedAtDesc(propertyId, CashShiftStatus.OPEN)
                .isPresent()) {
            throw conflict("Für dieses Hotel ist bereits eine Kassenschicht geöffnet.");
        }
        CashShift shift = new CashShift();
        shift.setProperty(property);
        shift.setStatus(CashShiftStatus.OPEN);
        shift.setOpeningFloat(money(request.openingFloat()));
        shift.setOpenedBy(clean(username) == null ? "system" : clean(username));
        shift.setNotes(clean(request.notes()));
        cashShiftRepository.save(shift);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse closeCashShift(Company company,
                                                Long propertyId,
                                                CloseCashShiftRequest request,
                                                String username,
                                                LocalDate businessDate) {
        lockProperty(company, propertyId);
        CashShift shift = cashShiftRepository
                .findFirstByProperty_IdAndStatusOrderByOpenedAtDesc(propertyId, CashShiftStatus.OPEN)
                .orElseThrow(() -> conflict("Es ist keine Kassenschicht geöffnet."));
        BigDecimal movements = cashMovements(shift);
        BigDecimal expected = money(shift.getOpeningFloat().add(movements));
        BigDecimal actual = money(request.actualCash());
        shift.setStatus(CashShiftStatus.CLOSED);
        shift.setExpectedCash(expected);
        shift.setActualCash(actual);
        shift.setVariance(money(actual.subtract(expected)));
        shift.setClosedBy(clean(username) == null ? "system" : clean(username));
        shift.setClosedAt(LocalDateTime.now());
        if (clean(request.notes()) != null) {
            shift.setNotes(clean(request.notes()));
        }
        cashShiftRepository.save(shift);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse createMaintenanceWorkOrder(
            Company company,
            Long propertyId,
            CreateMaintenanceWorkOrderRequest request,
            String username,
            LocalDate businessDate) {
        HotelProperty property = lockProperty(company, propertyId);
        Room room = roomRepository.findByIdAndProperty_Company_Id(request.roomId(), company.getId())
                .orElseThrow(() -> notFound("Zimmer nicht gefunden."));
        if (!room.getProperty().getId().equals(propertyId)) {
            throw notFound("Zimmer nicht gefunden.");
        }
        RoomBlock block = null;
        if (request.blockRoom()) {
            LocalDate start = request.blockStartDate() == null ? today(property) : request.blockStartDate();
            LocalDate end = request.blockEndDate() == null ? start.plusDays(1) : request.blockEndDate();
            RoomBlockType blockType =
                    request.blockType() == null ? RoomBlockType.OUT_OF_ORDER : request.blockType();
            validateStay(start, end);
            if (roomBlockRepository.countRoomBlocks(
                    room.getId(), start, end, RoomBlockStatus.ACTIVE) > 0) {
                throw conflict("Das Zimmer ist in diesem Zeitraum bereits gesperrt.");
            }
            if (INVENTORY_BLOCKING_ROOM_BLOCK_TYPES.contains(blockType)
                    && reservationRepository.countOverlappingByRoom(
                    room.getId(), start, end, NON_INVENTORY_STATUSES, null) > 0) {
                throw conflict("Vor der Sperre müssen bestehende Reservierungen umgezogen oder geändert werden.");
            }
            block = new RoomBlock();
            block.setProperty(property);
            block.setRoom(room);
            block.setType(blockType);
            block.setStatus(RoomBlockStatus.ACTIVE);
            block.setStartDate(start);
            block.setEndDate(end);
            block.setReason(required(request.title()));
            block.setCreatedBy(clean(username) == null ? "system" : clean(username));
            roomBlockRepository.save(block);
        }
        MaintenanceWorkOrder order = new MaintenanceWorkOrder();
        order.setProperty(property);
        order.setRoom(room);
        order.setRoomBlock(block);
        order.setTitle(required(request.title()));
        order.setDescription(clean(request.description()));
        order.setPriority(request.priority());
        order.setStatus(MaintenanceStatus.OPEN);
        order.setAssignedTo(clean(request.assignedTo()));
        order.setDueDate(request.dueDate());
        order.setReportedBy(clean(username) == null ? "system" : clean(username));
        maintenanceWorkOrderRepository.save(order);
        emitMaintenance(property, order, "maintenance.created");
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse resolveMaintenanceWorkOrder(
            Company company,
            Long propertyId,
            Long workOrderId,
            ResolveMaintenanceWorkOrderRequest request,
            String username,
            LocalDate businessDate) {
        MaintenanceWorkOrder order = maintenanceWorkOrderRepository
                .findByIdAndProperty_Company_Id(workOrderId, company.getId())
                .orElseThrow(() -> notFound("Wartungsauftrag nicht gefunden."));
        if (!order.getProperty().getId().equals(propertyId)) {
            throw notFound("Wartungsauftrag nicht gefunden.");
        }
        if (order.getStatus() == MaintenanceStatus.RESOLVED
                || order.getStatus() == MaintenanceStatus.CANCELLED) {
            throw conflict("Der Wartungsauftrag ist bereits abgeschlossen.");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(MaintenanceStatus.RESOLVED);
        order.setResolutionNotes(required(request.resolutionNotes()));
        order.setResolvedBy(clean(username) == null ? "system" : clean(username));
        order.setResolvedAt(now);
        if (order.getRoomBlock() != null && order.getRoomBlock().getStatus() == RoomBlockStatus.ACTIVE) {
            RoomBlock block = order.getRoomBlock();
            block.setStatus(RoomBlockStatus.COMPLETED);
            block.setResolvedBy(order.getResolvedBy());
            block.setResolvedAt(now);
            roomBlockRepository.save(block);
        }
        maintenanceWorkOrderRepository.save(order);
        emitMaintenance(order.getProperty(), order, "maintenance.resolved");
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse updateHousekeepingTask(Company company,
                                                        Long propertyId,
                                                        Long taskId,
                                                        UpdateHousekeepingTaskRequest request,
                                                        LocalDate businessDate) {
        requireProperty(company, propertyId);
        HousekeepingTask task = housekeepingTaskRepository.findByIdAndProperty_Company_Id(taskId, company.getId())
                .orElseThrow(() -> notFound("Housekeeping-Aufgabe nicht gefunden."));
        if (!task.getProperty().getId().equals(propertyId)) {
            throw notFound("Housekeeping-Aufgabe nicht gefunden.");
        }
        task.setType(request.type());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setEstimatedMinutes(request.estimatedMinutes());
        task.setNotes(clean(request.notes()));
        task.setAssignedTo(clean(request.assignedTo()));
        if (request.status() == HousekeepingStatus.CLEAN) {
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
        }
        Room room = task.getRoom();
        room.setHousekeepingStatus(request.status());
        roomRepository.save(room);
        housekeepingTaskRepository.save(task);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    private void applyGuest(GuestProfile guest, UpsertGuestRequest request) {
        guest.setFirstName(required(request.firstName()));
        guest.setLastName(required(request.lastName()));
        guest.setEmail(lower(request.email()));
        guest.setPhone(clean(request.phone()));
        guest.setDateOfBirth(request.dateOfBirth());
        guest.setNationalityCode(upper(request.nationalityCode()));
        guest.setLanguageCode(clean(request.languageCode()) == null ? "de" : lower(request.languageCode()));
        guest.setNotes(clean(request.notes()));
        guest.setVip(Boolean.TRUE.equals(request.vip()));
    }

    private void applyRatePlan(RatePlan ratePlan,
                               UpsertRatePlanRequest request,
                               HotelProperty property,
                               boolean creating) {
        ratePlan.setCode(code(request.code()));
        ratePlan.setName(required(request.name()));
        ratePlan.setCurrencyCode(property.getCurrencyCode());
        ratePlan.setNightlyRate(money(request.nightlyRate()));
        ratePlan.setMinStay(request.minStay());
        ratePlan.setBreakfastIncluded(request.breakfastIncluded());
        ratePlan.setRefundable(request.refundable());
        if (request.active() != null) {
            ratePlan.setActive(request.active());
        } else if (creating) {
            ratePlan.setActive(true);
        }
    }

    private void applyReservation(Company company,
                                  Reservation reservation,
                                  UpsertReservationRequest request,
                                  Long excludeReservationId) {
        validateStay(request.arrivalDate(), request.departureDate());
        if (request.status() == ReservationStatus.CHECKED_IN
                || request.status() == ReservationStatus.CHECKED_OUT
                || request.status() == ReservationStatus.CANCELLED
                || request.status() == ReservationStatus.NO_SHOW) {
            throw badRequest("Dieser Reservierungsstatus kann nur über die vorgesehene Aktion gesetzt werden.");
        }
        GuestProfile guest = guestRepository.findByIdAndCompany_Id(request.guestId(), company.getId())
                .orElseThrow(() -> notFound("Gast nicht gefunden."));
        RoomType roomType = requireRoomType(company, request.propertyId(), request.roomTypeId());
        RatePlan ratePlan = ratePlanRepository.findByIdAndProperty_Company_Id(request.ratePlanId(), company.getId())
                .orElseThrow(() -> notFound("Ratenplan nicht gefunden."));
        if (!ratePlan.isActive()
                || !ratePlan.getProperty().getId().equals(request.propertyId())
                || !ratePlan.getRoomType().getId().equals(roomType.getId())) {
            throw badRequest("Der Ratenplan passt nicht zum gewählten Hotel und Zimmertyp.");
        }
        if (request.adults() + request.children() > roomType.getMaxOccupancy()) {
            throw badRequest("Die Belegung überschreitet die maximale Kapazität des Zimmertyps.");
        }
        ReservationStatus targetStatus = request.status() == null ? ReservationStatus.CONFIRMED : request.status();
        if (!NON_INVENTORY_STATUSES.contains(targetStatus)) {
            ensureCapacity(
                    request.propertyId(),
                    roomType.getId(),
                    request.arrivalDate(),
                    request.departureDate(),
                    excludeReservationId
            );
        }
        Room room = null;
        if (request.roomId() != null) {
            room = roomRepository.findByIdAndProperty_Company_Id(request.roomId(), company.getId())
                    .orElseThrow(() -> notFound("Zimmer nicht gefunden."));
            if (!room.getProperty().getId().equals(request.propertyId())
                    || !room.getRoomType().getId().equals(roomType.getId())) {
                throw badRequest("Das Zimmer passt nicht zum Hotel oder Zimmertyp.");
            }
            if (!NON_INVENTORY_STATUSES.contains(targetStatus) && reservationRepository.countOverlappingByRoom(
                    room.getId(),
                    request.arrivalDate(),
                    request.departureDate(),
                    NON_INVENTORY_STATUSES,
                    excludeReservationId
            ) > 0) {
                throw conflict("Das gewählte Zimmer ist im Zeitraum bereits belegt.");
            }
            if (!NON_INVENTORY_STATUSES.contains(targetStatus)) {
                ensureRoomNotBlocked(room, request.arrivalDate(), request.departureDate());
            }
        }
        Quote quote = quote(ratePlan, request.arrivalDate(), request.departureDate());
        if (quote.restriction() != null) {
            throw conflict(quote.restriction());
        }
        reservation.setGuest(guest);
        reservation.setRoomType(roomType);
        reservation.setRoom(room);
        reservation.setRatePlan(ratePlan);
        reservation.setArrivalDate(request.arrivalDate());
        reservation.setDepartureDate(request.departureDate());
        reservation.setAdults(request.adults());
        reservation.setChildren(request.children());
        reservation.setStatus(targetStatus);
        reservation.setSource(request.source() == null ? ReservationSource.DIRECT : request.source());
        if (request.guaranteeStatus() != null) {
            reservation.setGuaranteeStatus(request.guaranteeStatus());
        } else if (reservation.getGuaranteeStatus() == null) {
            reservation.setGuaranteeStatus(ReservationGuaranteeStatus.UNGUARANTEED);
        }
        LocalDateTime holdUntil = request.holdUntil();
        if (HOLD_STATUSES.contains(targetStatus)) {
            if (holdUntil == null && reservation.getHoldUntil() != null
                    && reservation.getHoldUntil().isAfter(LocalDateTime.now())) {
                holdUntil = reservation.getHoldUntil();
            }
            reservation.setHoldUntil(holdUntil == null ? LocalDateTime.now().plusHours(48) : holdUntil);
            if (!reservation.getHoldUntil().isAfter(LocalDateTime.now())) {
                throw badRequest("Die Haltefrist muss in der Zukunft liegen.");
            }
        } else {
            reservation.setHoldUntil(null);
        }
        reservation.setTotalAmount(quote.total());
        reservation.setCurrencyCode(ratePlan.getCurrencyCode());
        reservation.setNotes(clean(request.notes()));
    }

    private void ensureCapacity(Long propertyId,
                                Long roomTypeId,
                                LocalDate arrival,
                                LocalDate departure,
                                Long excludeReservationId) {
        long capacity = countSellableRooms(propertyId, roomTypeId);
        if (capacity == 0) {
            throw conflict("Für diesen Zimmertyp sind keine verkaufbaren Zimmer eingerichtet.");
        }
        LocalDate date = arrival;
        while (date.isBefore(departure)) {
            long capacityForDate = Math.max(0, capacity - roomBlockRepository.countInventoryBlockingRooms(
                    propertyId,
                    roomTypeId,
                    date,
                    date.plusDays(1),
                    RoomBlockStatus.ACTIVE,
                    INVENTORY_BLOCKING_ROOM_BLOCK_TYPES
            ));
            long sold = reservationRepository.countOverlappingByRoomType(
                    propertyId,
                    roomTypeId,
                    date,
                    date.plusDays(1),
                    NON_INVENTORY_STATUSES,
                    excludeReservationId
            );
            if (sold >= capacityForDate) {
                throw conflict("Der Zimmertyp ist am " + date + " ausgebucht.");
            }
            date = date.plusDays(1);
        }
    }

    private long maximumSold(Long propertyId,
                             Long roomTypeId,
                             LocalDate arrival,
                             LocalDate departure,
                             Long excludeReservationId) {
        long maximum = 0;
        LocalDate date = arrival;
        while (date.isBefore(departure)) {
            maximum = Math.max(maximum, reservationRepository.countOverlappingByRoomType(
                    propertyId,
                    roomTypeId,
                    date,
                    date.plusDays(1),
                    NON_INVENTORY_STATUSES,
                    excludeReservationId
            ));
            date = date.plusDays(1);
        }
        return maximum;
    }

    private long minimumAvailable(Long propertyId,
                                  Long roomTypeId,
                                  LocalDate arrival,
                                  LocalDate departure,
                                  Long excludeReservationId) {
        long physicalCapacity = countSellableRooms(propertyId, roomTypeId);
        long minimum = physicalCapacity;
        LocalDate date = arrival;
        while (date.isBefore(departure)) {
            long blocked = roomBlockRepository.countInventoryBlockingRooms(
                    propertyId,
                    roomTypeId,
                    date,
                    date.plusDays(1),
                    RoomBlockStatus.ACTIVE,
                    INVENTORY_BLOCKING_ROOM_BLOCK_TYPES
            );
            long sold = reservationRepository.countOverlappingByRoomType(
                    propertyId, roomTypeId, date, date.plusDays(1), NON_INVENTORY_STATUSES, excludeReservationId);
            minimum = Math.min(minimum, Math.max(0, physicalCapacity - blocked - sold));
            date = date.plusDays(1);
        }
        return minimum;
    }

    private Quote quote(RatePlan ratePlan, LocalDate arrival, LocalDate departure) {
        validateStay(arrival, departure);
        long nights = ChronoUnit.DAYS.between(arrival, departure);
        List<RateOverride> overrides =
                rateOverrideRepository.findAllByRatePlan_IdAndStayDateBetweenOrderByStayDateAsc(
                        ratePlan.getId(),
                        arrival,
                        departure
                );
        Map<LocalDate, RateOverride> byDate = overrides.stream()
                .collect(Collectors.toMap(RateOverride::getStayDate, Function.identity()));
        int requiredStay = ratePlan.getMinStay();
        BigDecimal total = BigDecimal.ZERO;
        LocalDate date = arrival;
        while (date.isBefore(departure)) {
            RateOverride override = byDate.get(date);
            if (override != null) {
                if (override.isClosed()) {
                    return new Quote(BigDecimal.ZERO, "Der Ratenplan ist am " + date + " geschlossen.");
                }
                if (date.equals(arrival) && override.isClosedArrival()) {
                    return new Quote(BigDecimal.ZERO, "Anreise ist am " + date + " geschlossen.");
                }
                requiredStay = Math.max(requiredStay, override.getMinStay());
                total = total.add(override.getPrice());
            } else {
                total = total.add(ratePlan.getNightlyRate());
            }
            date = date.plusDays(1);
        }
        RateOverride departureOverride = byDate.get(departure);
        if (departureOverride != null && departureOverride.isClosedDeparture()) {
            return new Quote(BigDecimal.ZERO, "Abreise ist am " + departure + " geschlossen.");
        }
        if (nights < requiredStay) {
            return new Quote(BigDecimal.ZERO, "Der Mindestaufenthalt beträgt " + requiredStay + " Nächte.");
        }
        return new Quote(money(total), null);
    }

    private void createFolioWithRoomCharges(Reservation reservation) {
        Folio folio = new Folio();
        folio.setReservation(reservation);
        folio.setCurrencyCode(reservation.getCurrencyCode());
        folio.setStatus(FolioStatus.OPEN);
        folio.setLabel("Hauptkonto");
        folioRepository.save(folio);
        saveRoomChargeItems(folio, reservation);
    }

    private void refreshRoomCharges(Reservation reservation) {
        Folio folio = folioRepository.findFirstByReservation_IdOrderByIdAsc(reservation.getId())
                .orElseThrow(() -> conflict("Zur Reservierung fehlt das Gastkonto."));
        if (folio.getStatus() != FolioStatus.OPEN) {
            throw conflict("Ein geschlossenes Gastkonto kann nicht aktualisiert werden.");
        }
        folioItemRepository.deleteAllByFolio_IdAndType(folio.getId(), FolioItemType.ROOM);
        saveRoomChargeItems(folio, reservation);
    }

    private void saveRoomChargeItems(Folio folio, Reservation reservation) {
        Map<LocalDate, RateOverride> overrides = rateOverrideRepository
                .findAllByRatePlan_IdAndStayDateBetweenOrderByStayDateAsc(
                        reservation.getRatePlan().getId(),
                        reservation.getArrivalDate(),
                        reservation.getDepartureDate()
                )
                .stream()
                .collect(Collectors.toMap(RateOverride::getStayDate, Function.identity()));
        LocalDate date = reservation.getArrivalDate();
        while (date.isBefore(reservation.getDepartureDate())) {
            BigDecimal nightly = Optional.ofNullable(overrides.get(date))
                    .map(RateOverride::getPrice)
                    .orElse(reservation.getRatePlan().getNightlyRate());
            FolioItem item = new FolioItem();
            item.setFolio(folio);
            item.setServiceDate(date);
            item.setType(FolioItemType.ROOM);
            item.setDescription("Übernachtung " + reservation.getRoomType().getName());
            item.setQuantity(BigDecimal.ONE);
            item.setUnitPrice(money(nightly));
            item.setTotalAmount(money(nightly));
            folioItemRepository.save(item);
            date = date.plusDays(1);
        }
    }

    private void markRoomDirty(HotelProperty property, Room room, LocalDate serviceDate) {
        room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        roomRepository.save(room);
        HousekeepingTask task = housekeepingTaskRepository.findByRoom_IdAndServiceDate(room.getId(), serviceDate)
                .orElseGet(HousekeepingTask::new);
        task.setProperty(property);
        task.setRoom(room);
        task.setServiceDate(serviceDate);
        task.setType(HousekeepingTaskType.DEPARTURE);
        task.setStatus(HousekeepingStatus.DIRTY);
        task.setPriority(90);
        task.setEstimatedMinutes(35);
        task.setCompletedAt(null);
        housekeepingTaskRepository.save(task);
    }

    private PmsOperationsResponse.GuestView toGuestView(GuestProfile guest) {
        return new PmsOperationsResponse.GuestView(
                guest.getId(),
                guest.getFirstName(),
                guest.getLastName(),
                guest.getEmail(),
                guest.getPhone(),
                guest.getDateOfBirth(),
                guest.getNationalityCode(),
                guest.getLanguageCode(),
                guest.getNotes(),
                guest.isVip()
        );
    }

    private PmsOperationsResponse.RatePlanView toRatePlanView(RatePlan ratePlan) {
        return new PmsOperationsResponse.RatePlanView(
                ratePlan.getId(),
                ratePlan.getRoomType().getId(),
                ratePlan.getRoomType().getName(),
                ratePlan.getCode(),
                ratePlan.getName(),
                ratePlan.getCurrencyCode(),
                ratePlan.getNightlyRate(),
                ratePlan.getMinStay(),
                ratePlan.isBreakfastIncluded(),
                ratePlan.isRefundable(),
                ratePlan.isActive()
        );
    }

    private PmsOperationsResponse.RateOverrideView toRateOverrideView(RateOverride override) {
        return new PmsOperationsResponse.RateOverrideView(
                override.getId(),
                override.getRatePlan().getId(),
                override.getStayDate(),
                override.getPrice(),
                override.getMinStay(),
                override.isClosed(),
                override.isClosedArrival(),
                override.isClosedDeparture()
        );
    }

    private PmsOperationsResponse.ReservationView toReservationView(Reservation reservation) {
        return new PmsOperationsResponse.ReservationView(
                reservation.getId(),
                reservation.getVersion(),
                reservation.getConfirmationCode(),
                reservation.getGroupBooking() == null ? null : reservation.getGroupBooking().getId(),
                reservation.getGroupBooking() == null ? null : reservation.getGroupBooking().getName(),
                reservation.getGuest().getId(),
                reservation.getGuest().getFirstName() + " " + reservation.getGuest().getLastName(),
                reservation.getGuest().getEmail(),
                reservation.getRoomType().getId(),
                reservation.getRoomType().getName(),
                reservation.getRoom() == null ? null : reservation.getRoom().getId(),
                reservation.getRoom() == null ? null : reservation.getRoom().getNumber(),
                reservation.getRatePlan().getId(),
                reservation.getRatePlan().getName(),
                reservation.getArrivalDate(),
                reservation.getDepartureDate(),
                reservation.getAdults(),
                reservation.getChildren(),
                reservation.getStatus(),
                reservation.getSource(),
                reservation.getGuaranteeStatus(),
                reservation.getHoldUntil(),
                reservation.getTotalAmount(),
                reservation.getCurrencyCode(),
                reservation.getNotes(),
                reservation.getCheckedInAt(),
                reservation.getCheckedOutAt(),
                reservation.getCancelledAt(),
                reservation.getNoShowAt(),
                reservation.getCancellationReason(),
                reservationStatusHistoryRepository.findAllByReservation_IdOrderByChangedAtDesc(reservation.getId())
                        .stream()
                        .map(history -> new PmsOperationsResponse.ReservationHistoryView(
                                history.getId(),
                                history.getFromStatus(),
                                history.getToStatus(),
                                history.getChangedBy(),
                                history.getChangedAt(),
                                history.getReason()
                        ))
                        .toList()
        );
    }

    private PmsOperationsResponse.RoomStateView toRoomStateView(Room room, Reservation currentReservation) {
        return new PmsOperationsResponse.RoomStateView(
                room.getId(),
                room.getRoomType().getId(),
                room.getRoomType().getName(),
                room.getNumber(),
                room.getFloor(),
                room.getOperationalStatus(),
                room.getHousekeepingStatus(),
                currentReservation == null ? null : toReservationView(currentReservation)
        );
    }

    private PmsOperationsResponse.HousekeepingTaskView toHousekeepingView(HousekeepingTask task) {
        return new PmsOperationsResponse.HousekeepingTaskView(
                task.getId(),
                task.getRoom().getId(),
                task.getRoom().getNumber(),
                task.getServiceDate(),
                task.getType(),
                task.getStatus(),
                task.getPriority(),
                task.getEstimatedMinutes(),
                task.getNotes(),
                task.getAssignedTo(),
                task.getCompletedAt()
        );
    }

    private PmsOperationsResponse.FolioView toFolioView(Folio folio) {
        List<FolioItem> items = folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folio.getId());
        List<Payment> payments = paymentRepository.findAllByFolio_IdOrderByReceivedAtAsc(folio.getId());
        BigDecimal charges = items.stream()
                .map(FolioItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.POSTED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PmsOperationsResponse.FolioView(
                folio.getId(),
                folio.getReservation().getId(),
                folio.getReservation().getConfirmationCode(),
                folio.getReservation().getGuest().getFirstName() + " "
                        + folio.getReservation().getGuest().getLastName(),
                folio.getLabel(),
                folio.getOrganization() == null ? null : folio.getOrganization().getId(),
                folio.getOrganization() == null ? null : folio.getOrganization().getName(),
                folio.getCurrencyCode(),
                folio.getStatus(),
                money(charges),
                money(paid),
                money(charges.subtract(paid)),
                items.stream().map(item -> new PmsOperationsResponse.FolioItemView(
                        item.getId(),
                        item.getServiceDate(),
                        item.getType(),
                        item.getDescription(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalAmount()
                )).toList(),
                payments.stream().map(payment -> new PmsOperationsResponse.PaymentView(
                        payment.getId(),
                        payment.getAmount(),
                        payment.getMethod(),
                        payment.getStatus(),
                        payment.getKind(),
                        payment.getOriginalPayment() == null ? null : payment.getOriginalPayment().getId(),
                        payment.getReference(),
                        payment.getReason(),
                        payment.getReceivedAt(),
                        payment.getCreatedBy(),
                        payment.getVoidedAt(),
                        payment.getVoidedBy()
                )).toList()
        );
    }

    private PmsOperationsResponse.CashShiftView toCashShiftView(CashShift shift) {
        BigDecimal movements = cashMovements(shift);
        BigDecimal expected = shift.getExpectedCash() == null
                ? money(shift.getOpeningFloat().add(movements))
                : shift.getExpectedCash();
        return new PmsOperationsResponse.CashShiftView(
                shift.getId(),
                shift.getStatus(),
                shift.getOpenedBy(),
                shift.getOpenedAt(),
                shift.getOpeningFloat(),
                movements,
                expected,
                shift.getActualCash(),
                shift.getVariance(),
                shift.getClosedBy(),
                shift.getClosedAt(),
                shift.getNotes()
        );
    }

    private PmsOperationsResponse.RoomBlockView toRoomBlockView(RoomBlock block) {
        return new PmsOperationsResponse.RoomBlockView(
                block.getId(), block.getRoom().getId(), block.getRoom().getNumber(), block.getType(),
                block.getStatus(), block.getStartDate(), block.getEndDate(), block.getReason(),
                block.getCreatedBy(), block.getCreatedAt(), block.getResolvedBy(), block.getResolvedAt());
    }

    private PmsOperationsResponse.MaintenanceWorkOrderView toMaintenanceView(MaintenanceWorkOrder order) {
        return new PmsOperationsResponse.MaintenanceWorkOrderView(
                order.getId(), order.getRoom().getId(), order.getRoom().getNumber(),
                order.getRoomBlock() == null ? null : order.getRoomBlock().getId(),
                order.getTitle(), order.getDescription(), order.getPriority(), order.getStatus(),
                order.getAssignedTo(), order.getDueDate(), order.getReportedBy(), order.getReportedAt(),
                order.getResolutionNotes(), order.getResolvedBy(), order.getResolvedAt());
    }

    private BigDecimal cashMovements(CashShift shift) {
        LocalDateTime from = shift.getOpenedAt() == null ? LocalDateTime.now() : shift.getOpenedAt();
        return money(paymentRepository
                .findAllByFolio_Reservation_Property_IdAndMethodAndStatusAndReceivedAtGreaterThanEqual(
                        shift.getProperty().getId(),
                        PaymentMethod.CASH,
                        PaymentStatus.POSTED,
                        from
                )
                .stream()
                .filter(payment -> shift.getClosedAt() == null || !payment.getReceivedAt().isAfter(shift.getClosedAt()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private HotelProperty requireProperty(Company company, Long propertyId) {
        requireCompany(company);
        return propertyRepository.findByIdAndCompany_Id(propertyId, company.getId())
                .orElseThrow(() -> notFound("Hotel nicht gefunden."));
    }

    private HotelProperty lockProperty(Company company, Long propertyId) {
        requireCompany(company);
        return propertyRepository.findByIdAndCompany_IdForUpdate(propertyId, company.getId())
                .orElseThrow(() -> notFound("Hotel nicht gefunden."));
    }

    private RoomType requireRoomType(Company company, Long propertyId, Long roomTypeId) {
        RoomType roomType = roomTypeRepository.findByIdAndProperty_Company_Id(roomTypeId, company.getId())
                .orElseThrow(() -> notFound("Zimmertyp nicht gefunden."));
        if (!roomType.getProperty().getId().equals(propertyId)) {
            throw badRequest("Der Zimmertyp gehört zu einem anderen Hotel.");
        }
        return roomType;
    }

    private Reservation requireReservation(Company company, Long reservationId) {
        requireCompany(company);
        return reservationRepository.findByIdAndProperty_Company_Id(reservationId, company.getId())
                .orElseThrow(() -> notFound("Reservierung nicht gefunden."));
    }

    private Folio requireOpenFolio(Company company, Long propertyId, Long folioId) {
        Folio folio = folioRepository.findByIdAndReservation_Property_Company_Id(folioId, company.getId())
                .orElseThrow(() -> notFound("Gastkonto nicht gefunden."));
        if (!folio.getReservation().getProperty().getId().equals(propertyId)) {
            throw notFound("Gastkonto nicht gefunden.");
        }
        if (folio.getStatus() != FolioStatus.OPEN) {
            throw conflict("Das Gastkonto ist bereits geschlossen.");
        }
        return folio;
    }

    private long countSellableRooms(Long propertyId, Long roomTypeId) {
        return roomRepository.countByProperty_IdAndRoomType_IdAndActiveTrueAndOperationalStatus(
                propertyId,
                roomTypeId,
                RoomOperationalStatus.IN_SERVICE
        );
    }

    private void ensureRoomReady(Room room) {
        if (!room.isActive() || room.getOperationalStatus() != RoomOperationalStatus.IN_SERVICE) {
            throw conflict("Das Zimmer ist nicht in Betrieb.");
        }
        if (room.getHousekeepingStatus() != HousekeepingStatus.CLEAN) {
            throw conflict("Das Zimmer muss vor dem Check-in als sauber markiert sein.");
        }
    }

    private void ensureAssignedRoomAvailable(Reservation reservation, Room room, Long excludeReservationId) {
        if (room == null) {
            return;
        }
        if (reservationRepository.countOverlappingByRoom(
                room.getId(),
                reservation.getArrivalDate(),
                reservation.getDepartureDate(),
                NON_INVENTORY_STATUSES,
                excludeReservationId
        ) > 0) {
            throw conflict("Das zugewiesene Zimmer ist im Zeitraum bereits belegt.");
        }
        ensureRoomNotBlocked(room, reservation.getArrivalDate(), reservation.getDepartureDate());
    }

    private void ensureRoomNotBlocked(Room room, LocalDate arrival, LocalDate departure) {
        if (roomBlockRepository.countInventoryBlockingRoomBlocks(
                room.getId(),
                arrival,
                departure,
                RoomBlockStatus.ACTIVE,
                INVENTORY_BLOCKING_ROOM_BLOCK_TYPES
        ) > 0) {
            throw conflict("Das Zimmer ist im gewählten Zeitraum gesperrt.");
        }
    }

    private void transition(Reservation reservation,
                            ReservationStatus targetStatus,
                            String username,
                            String reason) {
        ReservationStatus previous = reservation.getStatus();
        reservation.setStatus(targetStatus);
        recordHistory(reservation, previous, targetStatus, username, reason);
    }

    private void recordHistory(Reservation reservation,
                               ReservationStatus fromStatus,
                               ReservationStatus toStatus,
                               String username,
                               String reason) {
        ReservationStatusHistory history = new ReservationStatusHistory();
        history.setReservation(reservation);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(clean(username) == null ? "system" : clean(username));
        history.setReason(clean(reason));
        reservationStatusHistoryRepository.save(history);
    }

    private void validateStay(LocalDate arrival, LocalDate departure) {
        if (arrival == null || departure == null || !departure.isAfter(arrival)) {
            throw badRequest("Die Abreise muss nach der Anreise liegen.");
        }
        if (ChronoUnit.DAYS.between(arrival, departure) > 365) {
            throw badRequest("Ein Aufenthalt darf höchstens 365 Nächte umfassen.");
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw badRequest("Der Zeitraum ist ungültig.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 400) {
            throw badRequest("Der angefragte Zeitraum ist zu gross.");
        }
    }

    private LocalDate today(HotelProperty property) {
        return LocalDate.now(ZoneId.of(property.getTimezone()));
    }

    private String generateConfirmationCode() {
        return "CHR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String code(String value) {
        return required(value).toUpperCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    private String required(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw badRequest("Ein Pflichtfeld ist leer.");
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String lower(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private String upper(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private void requireCompany(Company company) {
        if (company == null || company.getId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Eine Firmenzuordnung ist erforderlich.");
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException badGateway(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private PmsPaymentGateway gatewayFor(PaymentMethod method) {
        return paymentGateways.stream()
                .filter(gateway -> gateway.supports(method))
                .findFirst()
                .orElseThrow(() -> conflict(
                        "Kartenzahlungen benötigen einen konfigurierten Zahlungsprovider."));
    }

    private void emit(Reservation reservation, String eventType) {
        String payload = "{\"reservationId\":" + reservation.getId()
                + ",\"confirmationCode\":\"" + reservation.getConfirmationCode()
                + "\",\"status\":\"" + reservation.getStatus() + "\"}";
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setProperty(reservation.getProperty());
        event.setEventType(eventType);
        event.setAggregateType("reservation");
        event.setAggregateId(String.valueOf(reservation.getId()));
        event.setPayload(payload);
        outboxRepository.save(event);
        auditWriter.append(reservation.getProperty(), eventType, "reservation",
                String.valueOf(reservation.getId()), payload);
    }

    private void emitMaintenance(HotelProperty property, MaintenanceWorkOrder order, String eventType) {
        String payload = "{\"workOrderId\":" + order.getId()
                + ",\"roomId\":" + order.getRoom().getId()
                + ",\"status\":\"" + order.getStatus() + "\"}";
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setProperty(property);
        event.setEventType(eventType);
        event.setAggregateType("maintenance");
        event.setAggregateId(String.valueOf(order.getId()));
        event.setPayload(payload);
        outboxRepository.save(event);
        auditWriter.append(property, eventType, "maintenance", String.valueOf(order.getId()), payload);
    }

    private record Quote(BigDecimal total, String restriction) {
    }
}
