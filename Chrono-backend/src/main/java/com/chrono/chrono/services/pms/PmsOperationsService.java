package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.data.domain.PageRequest;
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
@org.springframework.context.annotation.Import({PmsGroupInventoryService.class,PmsGroupRoutingService.class})
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
    private final PmsOrganizationRepository organizationRepository;
    private final GroupBookingRepository groupRepository;
    private final GuestCommunicationRepository communicationRepository;
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
    private final PmsHousekeepingService housekeepingWork;
    private final IntegrationOutboxRepository outboxRepository;
    private final PmsAuditWriter auditWriter;
    private final List<PmsPaymentGateway> paymentGateways;
    private final PmsFinancialPeriodService financialPeriods;
    private final PmsInvoiceLineRepository invoiceLineRepository;
    private final PmsCashService cashService;
    private final PmsRefundProcessor refundProcessor;
    private final PmsReservationPolicyService reservationPolicies;
    private final PosTicketRepository posTickets;
    private final PmsDocumentFingerprintService documentFingerprints;
    private final PmsGroupInventoryService groupInventory;
    private final PmsGroupRoutingService groupRouting;
    private final org.springframework.transaction.support.TransactionTemplate readTransaction;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager lifecycleEntityManager;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PmsRateInheritanceService rateInheritance;

    public PmsOperationsService(HotelPropertyRepository propertyRepository,
                                RoomTypeRepository roomTypeRepository,
                                RoomRepository roomRepository,
                                GuestProfileRepository guestRepository,
                                PmsOrganizationRepository organizationRepository,
                                GroupBookingRepository groupRepository,
                                GuestCommunicationRepository communicationRepository,
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
                                PmsHousekeepingService housekeepingWork,
                                IntegrationOutboxRepository outboxRepository,
                                PmsAuditWriter auditWriter,
                                List<PmsPaymentGateway> paymentGateways,
                                PmsFinancialPeriodService financialPeriods,
                                PmsInvoiceLineRepository invoiceLineRepository,
                                PmsCashService cashService, PmsRefundProcessor refundProcessor, PosTicketRepository posTickets,
                                PmsDocumentFingerprintService documentFingerprints,
                                org.springframework.transaction.PlatformTransactionManager transactions,
                                PmsGroupInventoryService groupInventory, PmsGroupRoutingService groupRouting, PmsReservationPolicyService reservationPolicies) {
        this.propertyRepository = propertyRepository;
        this.groupInventory = groupInventory;
        this.groupRouting = groupRouting;
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.guestRepository = guestRepository;
        this.organizationRepository = organizationRepository;
        this.groupRepository = groupRepository;
        this.communicationRepository = communicationRepository;
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
        this.housekeepingWork = housekeepingWork;
        this.outboxRepository = outboxRepository;
        this.auditWriter = auditWriter;
        this.paymentGateways = List.copyOf(paymentGateways);
        this.financialPeriods = financialPeriods;
        this.invoiceLineRepository = invoiceLineRepository;
        this.cashService = cashService;
        this.refundProcessor = refundProcessor;
        this.reservationPolicies = reservationPolicies;
        this.posTickets = posTickets;
        this.documentFingerprints = documentFingerprints;
        this.readTransaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.readTransaction.setReadOnly(true);
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
        Map<Long, List<ReservationStatusHistory>> historiesByReservation = reservations.isEmpty() ? Map.of()
                : reservationStatusHistoryRepository.findAllByReservation_IdInOrderByChangedAtDescIdDesc(
                        reservations.stream().map(Reservation::getId).toList()).stream()
                        .collect(Collectors.groupingBy(history -> history.getReservation().getId()));
        List<PmsOperationsResponse.ReservationView> reservationViews = reservations.stream()
                .map(reservation -> toReservationView(reservation,
                        historiesByReservation.getOrDefault(reservation.getId(), List.of())))
                .toList();
        Map<Long, PmsOperationsResponse.ReservationView> viewsByReservation = reservationViews.stream()
                .collect(Collectors.toMap(PmsOperationsResponse.ReservationView::id, Function.identity()));
        List<PmsOperationsResponse.ReservationView> arrivals = reservationViews.stream()
                .filter(view -> view.arrivalDate().equals(safeDate))
                .filter(view -> OPERATIONAL_ARRIVAL_STATUSES.contains(view.status()))
                .toList();
        List<PmsOperationsResponse.ReservationView> departures = reservationViews.stream()
                .filter(view -> view.departureDate().equals(safeDate))
                .filter(view -> OPERATIONAL_DEPARTURE_STATUSES.contains(view.status()))
                .toList();

        List<GuestProfile> guests = searchGuestEntities(company.getId(), null, 200);
        List<RatePlan> ratePlans = ratePlanRepository.findAllByProperty_IdOrderByRoomType_SortOrderAscNameAsc(propertyId);
        List<RateOverride> rateOverrides =
                rateOverrideRepository.findAllByRatePlan_Property_IdAndStayDateBetweenOrderByStayDateAsc(
                        propertyId,
                        rangeStart,
                        rangeEnd
                );
        List<Room> rooms = roomRepository.findAllByProperty_IdOrderByFloorAscNumberAsc(propertyId);
        List<Folio> folios = folioRepository.findOperationalFolios(
                propertyId, rangeStart, rangeEnd, FolioStatus.OPEN);
        List<Long> folioIds = folios.stream().map(Folio::getId).toList();
        Map<Long, List<FolioItem>> itemsByFolio = folioIds.isEmpty() ? Map.of()
                : folioItemRepository.findAllByFolio_IdInOrderByServiceDateAscIdAsc(folioIds).stream()
                .collect(Collectors.groupingBy(item -> item.getFolio().getId()));
        Map<Long, List<Payment>> paymentsByFolio = folioIds.isEmpty() ? Map.of()
                : paymentRepository.findAllByFolio_IdInOrderByReceivedAtAsc(folioIds).stream()
                .collect(Collectors.groupingBy(payment -> payment.getFolio().getId()));
        Set<Long> invoicedIds = folioIds.isEmpty() ? Set.of()
                : new HashSet<>(invoiceLineRepository.findAllocatedSourceIdsByFolioIds(folioIds));
        List<PmsOperationsResponse.FolioView> folioViews = folios.stream()
                .map(folio -> toFolioView(folio,
                        itemsByFolio.getOrDefault(folio.getId(), List.of()),
                        paymentsByFolio.getOrDefault(folio.getId(), List.of()), invoicedIds))
                .toList();
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
                PmsMoney.round(openBalance, property.getCurrencyCode())
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
                organizationRepository.searchDirectory(company.getId(),"",true,false,org.springframework.data.domain.PageRequest.of(0,50)).stream()
                        .filter(PmsOrganization::isActive)
                        .map(this::toOrganizationSummaryView)
                        .toList(),
                ratePlans.stream().map(this::toRatePlanView).toList(),
                rateOverrides.stream().map(this::toRateOverrideView).toList(),
                rooms.stream().map(room -> toRoomStateView(room, currentByRoom.containsKey(room.getId())
                        ? viewsByReservation.get(currentByRoom.get(room.getId()).getId()) : null)).toList(),
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
                maintenanceWorkOrderRepository.findAllByProperty_IdOrderByReportedAtDesc(
                                propertyId, PageRequest.of(0, 200))
                        .stream()
                        .map(this::toMaintenanceView)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<PmsOperationsResponse.GuestView> searchGuests(Company company, Long propertyId,
                                                              String query, int limit) {
        requireProperty(company, propertyId);
        return searchGuestEntities(company.getId(), query, Math.max(1, Math.min(limit, 100)))
                .stream().map(this::toGuestView).toList();
    }

    private List<GuestProfile> searchGuestEntities(Long companyId, String query, int limit) {
        String normalized = clean(query);
        String pattern = normalized == null ? "%%" : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        return guestRepository.searchForOperations(companyId, pattern, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Company company,
                                                Long propertyId,
                                                LocalDate arrival,
                                                LocalDate departure) {
        return getAvailability(company, propertyId, arrival, departure, 1, 0, null);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Company company, Long propertyId, LocalDate arrival,
                                                LocalDate departure, int adults, int children, Long guestId) {
        return getAvailability(company, propertyId, arrival, departure, adults, children, guestId, null);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Company company, Long propertyId, LocalDate arrival,
                                                LocalDate departure, int adults, int children, Long guestId, Long organizationId) {
        HotelProperty property = requireProperty(company, propertyId);
        validateStay(arrival, departure);
        if (adults < 1 || children < 0 || adults > 100 || children > 100) {
            throw badRequest("Bitte eine gültige Anzahl Erwachsener und Kinder angeben.");
        }
        GuestProfile bookingGuest = guestId == null ? null : guestRepository.findByIdAndCompany_Id(guestId, company.getId())
                .filter(GuestProfile::isActive).orElseThrow(() -> notFound("Gast nicht gefunden."));
        PmsOrganization bookingOrganization = bookingGuest == null ? null : bookingGuest.getOrganization();
        if (bookingGuest == null && organizationId != null) {
            bookingOrganization = organizationRepository.findByIdAndCompany_Id(organizationId, company.getId())
                    .filter(PmsOrganization::isActive).orElseThrow(() -> notFound("Firma nicht gefunden."));
        }
        final PmsOrganization quotedOrganization = bookingOrganization;
        List<RoomType> roomTypes = roomTypeRepository.findAllByProperty_IdOrderBySortOrderAscNameAsc(propertyId);
        List<RatePlan> ratePlans = ratePlanRepository.findAllByProperty_IdOrderByRoomType_SortOrderAscNameAsc(propertyId);
        List<Room> rooms = roomRepository.findAllByProperty_IdOrderByFloorAscNumberAsc(propertyId);
        List<Reservation> overlappingReservations = reservationRepository
                .findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
                        propertyId, departure, arrival);
        List<RoomBlock> overlappingBlocks = roomBlockRepository
                .findAllByProperty_IdAndStartDateLessThanAndEndDateGreaterThanOrderByStartDateAsc(
                        propertyId, departure, arrival);
        Map<Long, List<RateOverride>> overridesByRatePlan = rateOverrideRepository
                .findAllByRatePlan_Property_IdAndStayDateBetweenOrderByStayDateAsc(propertyId, arrival, departure)
                .stream().collect(Collectors.groupingBy(value -> value.getRatePlan().getId()));

        List<AvailabilityResponse.RoomTypeAvailability> availability = roomTypes.stream()
                .filter(RoomType::isActive)
                .map(roomType -> {
                    InventorySummary inventory = summarizeInventory(roomType.getId(), arrival, departure,
                            rooms, overlappingReservations, overlappingBlocks);
                    long total = inventory.total();
                    long sold = inventory.maximumSold();
                    long available = inventory.minimumAvailable();
                    List<AvailabilityResponse.AvailableRoom> freeRooms = rooms.stream()
                            .filter(Room::isActive)
                            .filter(room -> room.getOperationalStatus() == RoomOperationalStatus.IN_SERVICE)
                            .filter(room -> room.getRoomType().getId().equals(roomType.getId()))
                            .filter(room -> reservationRepository.countOverlappingByRoom(room.getId(), arrival, departure, NON_INVENTORY_STATUSES, null) == 0)
                            .filter(room -> overlappingBlocks.stream().noneMatch(block ->
                                    block.getRoom().getId().equals(room.getId())
                                            && block.getStatus() == RoomBlockStatus.ACTIVE
                                            && INVENTORY_BLOCKING_ROOM_BLOCK_TYPES.contains(block.getType())))
                            .map(room -> new AvailabilityResponse.AvailableRoom(
                                    room.getId(),
                                    room.getNumber(),
                                    room.getFloor(),
                                    room.getHousekeepingStatus(),
                                    arrival.equals(today(property))
                                            && room.getHousekeepingStatus() == HousekeepingStatus.CLEAN
                            ))
                            .toList();
                    List<AvailabilityResponse.RateOption> rates = ratePlans.stream()
                            .filter(RatePlan::isActive)
                            .filter(ratePlan -> ratePlan.getRoomType().getId().equals(roomType.getId()))
                            .map(ratePlan -> {
                                Quote quote = quote(ratePlan, arrival, departure,
                                        overridesByRatePlan.getOrDefault(ratePlan.getId(), List.of()), adults, children);
                                String restriction = quote.restriction();
                                if (adults + children > roomType.getMaxOccupancy()) {
                                    restriction = "Die Belegung überschreitet die Zimmerkapazität.";
                                } else if (!eligibleForOrganization(ratePlan, quotedOrganization)) {
                                    restriction = "Diese Firmenrate benötigt einen zugeordneten Gast der Firma.";
                                }
                                boolean canBook = available > 0 && restriction == null;
                                return new AvailabilityResponse.RateOption(
                                        ratePlan.getId(),
                                        ratePlan.getCode(),
                                        ratePlan.getName(),
                                        ratePlan.getCurrencyCode(),
                                        quote.total(),
                                        canBook,
                                        available == 0 ? "Ausgebucht" : restriction
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
                            rates,
                            freeRooms
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
        createGuestRecord(company, propertyId, request);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    GuestProfile createGuestRecord(Company company,
                                   Long propertyId,
                                   UpsertGuestRequest request) {
        requireProperty(company, propertyId);
        GuestProfile guest = new GuestProfile();
        guest.setCompany(company);
        applyGuest(company, guest, request);
        guestRepository.save(guest);
        ensureGuestReference(guest);
        return guestRepository.save(guest);
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
        applyGuest(company, guest, request);
        ensureGuestReference(guest);
        guestRepository.save(guest);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse mergeGuest(Company company,
                                            Long propertyId,
                                            Long sourceGuestId,
                                            MergeGuestProfilesRequest request,
                                            LocalDate businessDate) {
        HotelProperty property = requireProperty(company, propertyId);
        GuestProfile source = guestRepository.findByIdAndCompany_Id(sourceGuestId, company.getId())
                .orElseThrow(() -> notFound("Quell-Gast nicht gefunden."));
        GuestProfile target = guestRepository.findByIdAndCompany_Id(request.targetGuestId(), company.getId())
                .orElseThrow(() -> notFound("Ziel-Gast nicht gefunden."));
        if (source.getId().equals(target.getId())) {
            throw badRequest("Quell- und Zielkartei müssen verschieden sein.");
        }
        if (!source.isActive() || source.getMergedInto() != null || !target.isActive()) {
            throw conflict("Nur aktive, noch nicht zusammengeführte Gästekarteien können verwendet werden.");
        }
        Set<String> fields = request.takeFromSource() == null ? Set.of() : request.takeFromSource();
        applyGuestMergeFields(source, target, fields);
        reservationRepository.findAllByGuest_IdOrderByArrivalDateDesc(source.getId()).forEach(value -> value.setGuest(target));
        groupRepository.findAllByContactGuest_Id(source.getId()).forEach(value -> value.setContactGuest(target));
        communicationRepository.findAllByGuest_IdOrderByCreatedAtDesc(source.getId()).forEach(value -> value.setGuest(target));
        source.setActive(false);
        source.setMergedInto(target);
        ensureGuestReference(target);
        guestRepository.save(target);
        guestRepository.save(source);
        auditWriter.append(property, "guest.merged", "guest", String.valueOf(source.getId()),
                "{\"targetGuestId\":" + target.getId() + "}");
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
        if (rateInheritance != null) rateInheritance.freezeOnManualEdit(ratePlanId);
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
        override.setRevenueManaged(false);
        override.setStayDate(request.stayDate());
        override.setPrice(PmsMoney.require(request.price(), ratePlan.getCurrencyCode()));
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
        return createReservationRecord(company,request,username,null);
    }

    @Transactional
    Reservation createReservationRecord(Company company,UpsertReservationRequest request,String username,GroupBooking group) {
        HotelProperty property = lockProperty(company, request.propertyId());
        Reservation reservation = new Reservation();
        reservation.setProperty(property);
        reservation.setGroupBooking(group);
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
    Reservation verifyPublicBookingRecord(Company company, Long reservationId, boolean guaranteeRequired) {
        Reservation current = requireReservation(company, reservationId);
        lockProperty(company, current.getProperty().getId());
        Reservation reservation = requireReservation(company, reservationId);
        if (reservation.getSource() != ReservationSource.BOOKING_ENGINE) {
            throw badRequest("Die Reservierung stammt nicht aus der Onlinebuchung.");
        }
        if (reservation.getStatus() != ReservationStatus.TENTATIVE) {
            return reservation;
        }
        if (guaranteeRequired) {
            reservation.setHoldUntil(LocalDateTime.now().plusHours(24));
            reservationRepository.save(reservation);
            recordHistory(reservation, ReservationStatus.TENTATIVE, ReservationStatus.TENTATIVE,
                    "booking-verification", "E-Mail bestätigt; Garantie ausstehend");
        } else {
            transition(reservation, ReservationStatus.CONFIRMED,
                    "booking-verification", "E-Mail bestätigt");
            reservation.setHoldUntil(null);
            reservationRepository.save(reservation);
        }
        emit(reservation, "reservation.public_booking_verified");
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
        if (!reservation.getRoomSegments().isEmpty()) {
            if (!request.arrivalDate().equals(reservation.getArrivalDate()) || !request.departureDate().equals(reservation.getDepartureDate())
                    || !Objects.equals(request.roomId(), reservation.getRoom() == null ? null : reservation.getRoom().getId())
                    || !request.ratePlanId().equals(reservation.getRatePlan().getId()) || !request.roomTypeId().equals(reservation.getRoomType().getId())) {
                throw conflict("Bei einem aufgeteilten Aufenthalt Zimmer und Raten über die Aufenthaltsabschnitte ändern.");
            }
        }
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
        Reservation reservation = requireLockedReservation(company, reservationId);
        HotelProperty property = lockProperty(company, reservation.getProperty().getId());
        List<String> blockers = checkInBlockers(reservation);
        if (!blockers.isEmpty()) {
            throw conflict(blockers.get(0));
        }
        transition(reservation, ReservationStatus.CHECKED_IN, username, "Check-in");
        reservation.setRoom(roomOn(reservation, today(property)));
        reservation.setCheckedInAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        emit(reservation, "reservation.checked_in");
        return getOperations(company, property.getId(), businessDate, null, null);
    }

    @Transactional(readOnly = true)
    public ReservationPolicyView getReservationPolicy(Company company, Long reservationId) {
        return reservationPolicies.view(requireReservation(company, reservationId));
    }

    List<String> checkInBlockers(Reservation reservation) {
        List<String> blockers = new ArrayList<>();
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            blockers.add("Nur bestätigte Reservierungen können eingecheckt werden.");
        }
        LocalDate now = today(reservation.getProperty());
        ReservationPolicyView policy = reservationPolicies.view(reservation);
        if (policy.depositOverdue()) blockers.add("Die fällige Anzahlung ist noch nicht vollständig eingegangen: "
                + policy.depositOutstandingAmount().toPlainString() + " " + reservation.getCurrencyCode() + ".");
        if (now.isBefore(reservation.getArrivalDate()) || !now.isBefore(reservation.getDepartureDate())) {
            blockers.add("Der Check-in liegt ausserhalb des gebuchten Aufenthalts.");
        }
        Room room = roomOn(reservation, today(reservation.getProperty()));
        if (room == null) {
            blockers.add("Vor dem Check-in muss ein Zimmer zugewiesen werden.");
            return blockers;
        }
        if (!room.isActive() || room.getOperationalStatus() != RoomOperationalStatus.IN_SERVICE) {
            blockers.add("Das Zimmer ist nicht in Betrieb.");
        }
        if (room.getHousekeepingStatus() != HousekeepingStatus.CLEAN) {
            blockers.add("Das Zimmer muss vor dem Check-in als sauber markiert sein.");
        }
        ReservationRoomSegment currentSegment = reservation.getRoomSegments().stream()
                .filter(segment -> !now.isBefore(segment.getStartDate()) && now.isBefore(segment.getEndDate())).findFirst().orElse(null);
        if (reservationRepository.countOverlappingByRoom(
                room.getId(),
                currentSegment == null ? reservation.getArrivalDate() : currentSegment.getStartDate(),
                currentSegment == null ? reservation.getDepartureDate() : currentSegment.getEndDate(),
                NON_INVENTORY_STATUSES,
                reservation.getId()
        ) > 0) {
            blockers.add("Das zugewiesene Zimmer ist bereits belegt.");
        }
        return blockers;
    }

    @Transactional
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
        Reservation reservation = requireLockedReservation(company, reservationId);
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN) {
            throw conflict("Nur eingecheckte Aufenthalte können ausgecheckt werden.");
        }
        List<Folio> folios = folioRepository.findAllByReservation_IdOrderByIdAsc(reservationId).stream()
                .filter(folio -> !folio.isGroupMaster()).toList();
        if (folios.isEmpty()) {
            throw conflict("Zur Reservierung fehlt das Gastkonto.");
        }
        if (paymentRepository.findAllByFolio_IdInOrderByReceivedAtAsc(folios.stream().map(Folio::getId).toList())
                .stream().anyMatch(payment -> payment.getStatus() == PaymentStatus.PENDING)) {
            throw conflict("Vor dem Check-out müssen offene Rückerstattungen beim Zahlungsanbieter geklärt werden.");
        }
        boolean unbalanced = folios.stream()
                .map(this::toFolioView)
                .anyMatch(view -> view.balance().signum() != 0);
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
            markRoomDirty(reservation.getProperty(), roomOn(reservation, today(reservation.getProperty())), today(reservation.getProperty()));
        }
        emit(reservation, "reservation.checked_out");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    @Transactional
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
        Reservation reservation = requireLockedReservation(company, reservationId);
        if (reservation.getStatus() != ReservationStatus.OFFERED
                && reservation.getStatus() != ReservationStatus.TENTATIVE
                && reservation.getStatus() != ReservationStatus.WAITLISTED
                && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw conflict("Nur offene Reservierungen können storniert werden.");
        }
        List<Folio> folios = folioRepository.findAllByReservation_IdOrderByIdAsc(reservationId);
        reservationPolicies.applyCancellation(reservation, false);
        groupRouting.route(reservation);
        String reason = request == null ? null : clean(request.reason());
        transition(reservation, ReservationStatus.CANCELLED, username,
                reason == null ? "Stornierung" : reason);
        reservation.setCancellationReason(reason);
        reservation.setCancelledAt(LocalDateTime.now());
        reservation.setHoldUntil(null);
        reservationRepository.save(reservation);
        folios.stream().filter(folio -> !folio.isGroupMaster()).forEach(folio -> {
            if (toFolioView(folio).balance().signum() == 0) {
                folio.setStatus(FolioStatus.CLOSED); folio.setClosedAt(LocalDateTime.now()); folioRepository.save(folio);
            }
        });
        emit(reservation, "reservation.cancelled");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    @Transactional
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
        Reservation reservation = requireLockedReservation(company, reservationId);
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw conflict("Nur bestätigte Reservierungen können als nicht angereist (No-Show) markiert werden.");
        }
        if (today(reservation.getProperty()).isBefore(reservation.getArrivalDate())) {
            throw conflict("Eine Reservierung kann nicht vor dem Anreisetag als nicht angereist (No-Show) markiert werden.");
        }
        reservationPolicies.applyCancellation(reservation, true);
        groupRouting.route(reservation);
        transition(reservation, ReservationStatus.NO_SHOW, username, "Nicht angereist (No-Show)");
        reservation.setNoShowAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        emit(reservation, "reservation.no_show");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    @Transactional
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
        lockProperty(company, reservation.getProperty().getId());
        if (reservation.getStatus() == ReservationStatus.CHECKED_OUT
                || reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.NO_SHOW) {
            throw conflict("Bei abgeschlossenen Reservierungen ist kein Zimmerwechsel möglich.");
        }
        Room target = roomRepository.findByIdAndProperty_Company_Id(request.roomId(), company.getId())
                .orElseThrow(() -> notFound("Zimmer nicht gefunden."));
        if (!target.getProperty().getId().equals(reservation.getProperty().getId()) || !target.isActive()
                || target.getOperationalStatus() != RoomOperationalStatus.IN_SERVICE) {
            throw badRequest("Das Zielzimmer muss im Hotel aktiv und in Betrieb sein.");
        }
        LocalDate effective = request.effectiveDate() == null
                ? (reservation.getStatus() == ReservationStatus.CHECKED_IN ? today(reservation.getProperty()) : reservation.getArrivalDate())
                : request.effectiveDate();
        if (effective.isBefore(reservation.getArrivalDate()) || !effective.isBefore(reservation.getDepartureDate())
                || reservation.getStatus() == ReservationStatus.CHECKED_IN && effective.isBefore(today(reservation.getProperty()))) {
            throw badRequest("Der Wechsel muss innerhalb des Aufenthalts liegen; belegte Vergangenheit darf nicht umgeschrieben werden.");
        }
        financialPeriods.assertPostingOpen(reservation.getProperty(), effective);
        if (reservation.getRoomSegments().isEmpty()) {
            if (reservation.getRoom() == null) {
                if (!effective.equals(reservation.getArrivalDate())) throw badRequest("Ein noch nicht zugewiesener Aufenthalt muss ab Anreise zugewiesen werden.");
                reservation.setRoom(target);
            }
            ReservationRoomSegment initial = new ReservationRoomSegment();
            initial.setReservation(reservation); initial.setRoom(reservation.getRoom()); initial.setRatePlan(reservation.getRatePlan());
            initial.setStartDate(reservation.getArrivalDate()); initial.setEndDate(reservation.getDepartureDate());
            initial.setCreatedBy(username); reservation.getRoomSegments().add(initial);
        }
        ReservationRoomSegment segment = reservation.getRoomSegments().stream()
                .filter(s -> !effective.isBefore(s.getStartDate()) && effective.isBefore(s.getEndDate())).findFirst()
                .orElseThrow(() -> conflict("Für das Wechseldatum fehlt ein Aufenthaltsabschnitt."));
        Room previous = segment.getRoom();
        RatePlan rate = request.ratePlanId() == null ? segment.getRatePlan()
                : ratePlanRepository.findByIdAndProperty_Company_Id(request.ratePlanId(), company.getId()).orElseThrow(() -> notFound("Ratenplan nicht gefunden."));
        if (!rate.getProperty().getId().equals(reservation.getProperty().getId()) || !rate.getRoomType().getId().equals(target.getRoomType().getId())
                || !rate.isActive() || !eligibleForRate(rate, reservation.getGuest())) throw badRequest("Für den Ziel-Zimmertyp einen gültigen Ratenplan auswählen.");
        if (reservation.getAdults() + reservation.getChildren() > target.getRoomType().getMaxOccupancy()) throw badRequest("Das Zielzimmer ist für die Belegung zu klein.");
        Quote segmentQuote = quote(rate, effective, segment.getEndDate(), reservation.getAdults(), reservation.getChildren(), reservation.getCreatedAt().toLocalDate());
        if (segmentQuote.restriction() != null) throw conflict(segmentQuote.restriction());
        ensureCapacity(reservation.getProperty().getId(), target.getRoomType().getId(), effective, segment.getEndDate(), reservation.getId());
        if (reservationRepository.countOverlappingByRoom(target.getId(), effective, segment.getEndDate(), NON_INVENTORY_STATUSES, reservation.getId()) > 0) throw conflict("Das Zielzimmer ist im Wechselzeitraum belegt.");
        ensureRoomNotBlocked(target, effective, segment.getEndDate());
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN && !effective.isAfter(today(reservation.getProperty()))) {
            ensureRoomReady(target);
            if (previous != null && !previous.getId().equals(target.getId())) {
                markRoomDirty(reservation.getProperty(), previous, today(reservation.getProperty()));
            }
        }
        if (effective.isAfter(segment.getStartDate())) {
            ReservationRoomSegment next = new ReservationRoomSegment();
            next.setReservation(reservation); next.setStartDate(effective); next.setEndDate(segment.getEndDate());
            next.setCreatedBy(username); reservation.getRoomSegments().add(next);
            segment.setEndDate(effective); segment = next;
        }
        segment.setRoom(target); segment.setRatePlan(rate); segment.setReason(clean(request.reason()));
        reservation.setRoom(roomOn(reservation, today(reservation.getProperty())));
        reservationRepository.save(reservation);
        refreshRoomCharges(reservation);
        reservation.setTotalAmount(folioItemRepository.findAllByFolio_Reservation_IdAndRateGeneratedTrueOrderByServiceDateAscIdAsc(reservationId)
                .stream().map(FolioItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        String reason = clean(request.reason());
        String description = "Zimmerwechsel "
                + (previous == null ? "ohne Zuweisung" : previous.getNumber())
                + " → " + target.getNumber()
                + (reason == null ? "" : ": " + reason);
        recordHistory(reservation, reservation.getStatus(), reservation.getStatus(), username, description);
        emit(reservation, "reservation.room_moved");
        return getOperations(company, reservation.getProperty().getId(), businessDate, null, null);
    }

    @Transactional(readOnly = true)
    public ReservationStayDetails getStayDetails(Company company, Long reservationId) {
        Reservation r = requireReservation(company, reservationId);
        List<ReservationStayDetails.RoomSegment> segments = r.getRoomSegments().stream().sorted(Comparator.comparing(ReservationRoomSegment::getStartDate))
                .map(s -> new ReservationStayDetails.RoomSegment(s.getId(), s.getRoom().getId(), s.getRoom().getNumber(), s.getRoom().getRoomType().getId(),
                        s.getRoom().getRoomType().getName(), s.getRatePlan().getId(), s.getStartDate(), s.getEndDate(), s.getReason())).toList();
        if (segments.isEmpty() && r.getRoom() != null) segments = List.of(new ReservationStayDetails.RoomSegment(null, r.getRoom().getId(), r.getRoom().getNumber(),
                r.getRoomType().getId(), r.getRoomType().getName(), r.getRatePlan().getId(), r.getArrivalDate(), r.getDepartureDate(), null));
        return new ReservationStayDetails(r.getId(), segments, r.getCoGuests().stream().map(g -> new ReservationStayDetails.CoGuest(g.getId(), g.getGuest().getId(),
                g.getGuest().getFirstName() + " " + g.getGuest().getLastName(), g.getArrivalDate(), g.getDepartureDate(), g.isChild(), g.getRegistrationCompletedAt())).toList());
    }

    @Transactional
    public ReservationStayDetails updateCoGuests(Company company, Long reservationId, UpsertReservationGuestsRequest request, String username) {
        Reservation r = requireReservation(company, reservationId); lockProperty(company, r.getProperty().getId());
        if (NON_INVENTORY_STATUSES.contains(r.getStatus())) throw conflict("Mitreisende können nur für aktive Aufenthalte geändert werden.");
        Set<Long> seen = new HashSet<>();
        Map<Long, ReservationGuest> existing = r.getCoGuests().stream().collect(Collectors.toMap(g -> g.getGuest().getId(), Function.identity()));
        List<ReservationGuest> selected = new ArrayList<>();
        for (var entry : request.guests()) {
            if (!seen.add(entry.guestId()) || entry.guestId().equals(r.getGuest().getId())) throw badRequest("Jede mitreisende Person einmal erfassen; der Hauptgast ist bereits zugeordnet.");
            if (entry.arrivalDate().isBefore(r.getArrivalDate()) || entry.departureDate().isAfter(r.getDepartureDate()) || !entry.departureDate().isAfter(entry.arrivalDate())) throw badRequest("Reisedaten der Mitreisenden müssen innerhalb des Aufenthalts liegen.");
            GuestProfile guest = guestRepository.findByIdAndCompany_Id(entry.guestId(), company.getId()).filter(GuestProfile::isActive).orElseThrow(() -> notFound("Gast nicht gefunden."));
            ReservationGuest value = existing.getOrDefault(entry.guestId(), new ReservationGuest());
            value.setReservation(r); value.setGuest(guest); value.setArrivalDate(entry.arrivalDate()); value.setDepartureDate(entry.departureDate()); value.setChild(entry.child());
            selected.add(value);
        }
        for (LocalDate date = r.getArrivalDate(); date.isBefore(r.getDepartureDate()); date = date.plusDays(1)) {
            LocalDate day = date;
            long adults = 1 + selected.stream().filter(g -> !g.isChild() && !day.isBefore(g.getArrivalDate()) && day.isBefore(g.getDepartureDate())).count();
            long children = selected.stream().filter(g -> g.isChild() && !day.isBefore(g.getArrivalDate()) && day.isBefore(g.getDepartureDate())).count();
            if (adults > r.getAdults() || children > r.getChildren()) throw badRequest("Die Mitreisenden überschreiten die gebuchte Erwachsenen-/Kinderzahl.");
        }
        r.getCoGuests().removeIf(g -> !seen.contains(g.getGuest().getId()));
        selected.stream().filter(g -> g.getId() == null).forEach(r.getCoGuests()::add);
        reservationRepository.saveAndFlush(r); recordHistory(r, r.getStatus(), r.getStatus(), username, "Mitreisende aktualisiert");
        return getStayDetails(company, reservationId);
    }

    @Transactional
    public ReservationStayDetails registerCoGuest(Company company, Long reservationId, Long guestId, CompleteGuestRegistrationRequest request, String username) {
        Reservation r = requireReservation(company, reservationId); lockProperty(company, r.getProperty().getId());
        ReservationGuest guest = r.getCoGuests().stream().filter(g -> g.getGuest().getId().equals(guestId)).findFirst().orElseThrow(() -> notFound("Mitreisender nicht gefunden."));
        if (!request.privacyConsent()) throw badRequest("Die erforderliche Bestätigung fehlt.");
        guest.setAddressLine(request.addressLine()); guest.setPostalCode(request.postalCode()); guest.setCity(request.city());
        guest.setCountryCode(request.countryCode().toUpperCase(Locale.ROOT)); guest.setNationalityCode(request.nationalityCode().toUpperCase(Locale.ROOT));
        guest.setDocumentHash(documentFingerprints.fingerprint(request.documentNumber()));
        guest.setDocumentLastFour(request.documentNumber().substring(request.documentNumber().length() - 4));
        guest.setSignatureName(request.signatureName()); guest.setRegistrationCompletedAt(LocalDateTime.now());
        reservationRepository.save(r); recordHistory(r, r.getStatus(), r.getStatus(), username, "Mitreisenden-Anmeldung abgeschlossen");
        return getStayDetails(company, reservationId);
    }

    private Room roomOn(Reservation r, LocalDate date) {
        if (r.getRoomSegments().isEmpty()) return r.getRoom();
        LocalDate day = date.isBefore(r.getArrivalDate()) ? r.getArrivalDate() : !date.isBefore(r.getDepartureDate()) ? r.getDepartureDate().minusDays(1) : date;
        return r.getRoomSegments().stream().filter(s -> !day.isBefore(s.getStartDate()) && day.isBefore(s.getEndDate())).map(ReservationRoomSegment::getRoom).findFirst().orElse(r.getRoom());
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
            folioRepository.findAllByReservation_IdOrderByIdAsc(reservation.getId()).stream().filter(folio -> !folio.isGroupMaster()).forEach(folio -> {
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
        HotelProperty property = lockProperty(company, propertyId);
        Folio folio = requireOpenFolio(company, propertyId, folioId);
        financialPeriods.assertPostingOpen(property, request.serviceDate());
        if (request.taxRate() == null) throw badRequest("Für die Leistung ist ein expliziter Steuersatz erforderlich (0 bei steuerfrei).");
        FolioItem item = new FolioItem();
        item.setFolio(folio);
        item.setServiceDate(request.serviceDate());
        item.setType(request.type());
        item.setDescription(required(request.description()));
        try { item.setQuantity(request.quantity().setScale(2, RoundingMode.UNNECESSARY)); }
        catch (ArithmeticException error) { throw badRequest("Die Menge darf höchstens zwei Nachkommastellen enthalten."); }
        item.setUnitPrice(PmsMoney.require(request.unitPrice(), property.getCurrencyCode()));
        item.setTotalAmount(PmsMoney.round(item.getQuantity().multiply(item.getUnitPrice()), property.getCurrencyCode()));
        item.setTaxRate(request.taxRate());
        item.setTaxIncluded(true);
        folioItemRepository.save(item);
        groupRouting.route(folio.getReservation());
        return getOperations(company, propertyId, businessDate, null, null);
    }

    @Transactional
    public PmsOperationsResponse postPayment(Company company,
                                             Long propertyId,
                                             Long folioId,
                                             PostPaymentRequest request,
                                             String username,
                                             LocalDate businessDate) {
        HotelProperty property = lockProperty(company, propertyId);
        Folio folio = requireOpenFolio(company, propertyId, folioId);
        if ("DIRECT_BILL".equals(request.method().name())) throw badRequest("Firmenforderungen müssen über die Debitorenfreigabe gebucht werden.");
        BigDecimal balance = toFolioView(folio).balance();
        financialPeriods.assertPostingOpen(property, financialPeriods.currentBusinessDate(property));
        if (request.amount().compareTo(balance) > 0) {
            throw badRequest("Die Zahlung darf den offenen Betrag nicht überschreiten.");
        }
        String paymentReference = clean(request.reference());
        Payment knownCardPayment = request.method() == PaymentMethod.CARD && paymentReference != null
                ? paymentRepository.findByProviderTransactionId(paymentReference).filter(p -> p.getFolio().getId().equals(folioId)).orElse(null) : null;
        String merchantContext = request.method() == PaymentMethod.CARD ? knownCardPayment == null
                ? gatewayFor(PaymentMethod.CARD).merchantContext(property) : knownCardPayment.getMerchantContext() : null;
        if (request.method() == PaymentMethod.CARD) {
            try {
                paymentReference = gatewayFor(PaymentMethod.CARD).verifyCapturedPayment(
                        property, folio, PmsMoney.require(request.amount(), property.getCurrencyCode()), paymentReference, merchantContext);
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (Exception exception) {
                throw badGateway("Die Kartenzahlung konnte beim Zahlungsprovider nicht bestätigt werden.");
            }
            Payment existing = paymentRepository.findByProviderTransactionId(paymentReference).orElse(null);
            if (existing != null) {
                boolean exactRetry = existing.getFolio().getId().equals(folioId)
                        && existing.getAmount().compareTo(PmsMoney.require(request.amount(), property.getCurrencyCode())) == 0
                        && existing.getMethod() == PaymentMethod.CARD
                        && java.util.Objects.equals(existing.getMerchantContext(), merchantContext)
                        && existing.getKind() == PaymentKind.PAYMENT;
                if (!exactRetry) {
                    throw conflict("Diese Provider-Zahlung wurde bereits anderweitig verbucht.");
                }
                return getOperations(company, propertyId, businessDate, null, null);
            }
        }
        Payment payment = new Payment();
        payment.setFolio(folio);
        payment.setAmount(PmsMoney.require(request.amount(), property.getCurrencyCode()));
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.POSTED);
        payment.setKind(PaymentKind.PAYMENT);
        payment.setReference(paymentReference);
        payment.setProviderTransactionId(request.method() == PaymentMethod.CARD ? paymentReference : null);
        payment.setMerchantContext(merchantContext);
        payment.setCreatedBy(clean(username) == null ? "system" : clean(username));
        payment.setPostingDate(financialPeriods.currentBusinessDate(property));
        if (request.method() == PaymentMethod.CASH) {
            payment.setCashShift(cashService.requireOpenShift(property, request.cashShiftId(), username));
        }
        paymentRepository.save(payment);
        emit(folio.getReservation(), "payment.posted");
        return getOperations(company, propertyId, businessDate, null, null);
    }

    public PmsOperationsResponse refundPayment(Company company, Long propertyId, Long paymentId,
                                               RefundPaymentRequest request, String username, LocalDate businessDate) {
        refundProcessor.process(company.getId(), propertyId, paymentId, request, username);
        return readTransaction.execute(status -> getOperations(company, propertyId, businessDate, null, null));
    }
    @Transactional
    public PmsOperationsResponse voidPayment(Company company,
                                             Long propertyId,
                                             Long paymentId,
                                             VoidPaymentRequest request,
                                             String username,
                                             LocalDate businessDate) {
        lockProperty(company, propertyId);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId, propertyId, company.getId())
                .orElseThrow(() -> notFound("Zahlung nicht gefunden."));
        if(payment.getFolio().getStatus()!=FolioStatus.OPEN)
            throw conflict("Auf einem geschlossenen Gastkonto Zahlungen über Gutschrift und Rückerstattung korrigieren.");
        if ("DIRECT_BILL".equals(payment.getMethod().name())) throw conflict("Firmenforderungen über die Debitorenkorrektur bearbeiten.");
        if (payment.getMethod() == PaymentMethod.CARD || payment.getKind() == PaymentKind.REFUND) {
            throw conflict("Bereits eingezogene Kartenzahlungen mit einem eindeutigen Rückerstattungsvorgang korrigieren; gebuchte Rückerstattungen können nicht storniert werden.");
        }
        financialPeriods.assertPostingOpen(payment.getFolio().getReservation().getProperty(), payment.getPostingDate() == null ? payment.getReceivedAt().toLocalDate() : payment.getPostingDate());
        if (payment.getCashShift() != null && payment.getCashShift().getStatus() != CashShiftStatus.OPEN) throw conflict("Die Kassenschicht ist abgeschlossen; eine Rückerstattung in einer offenen Schicht verwenden.");
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
        String register = clean(request.registerCode()) == null ? "FRONTDESK" : request.registerCode().trim().toUpperCase(Locale.ROOT);
        if (cashShiftRepository.existsByProperty_IdAndRegisterCodeAndStatus(propertyId, register, CashShiftStatus.OPEN)) {
            throw conflict("Für diese Kasse ist bereits eine Kassenschicht geöffnet.");
        }
        CashShift shift = new CashShift();
        shift.setProperty(property);
        shift.setRegisterCode(register);
        shift.setOutletCode(clean(request.outletCode()) == null ? "FRONTDESK" : request.outletCode().trim().toUpperCase(Locale.ROOT));
        shift.setStatus(CashShiftStatus.OPEN);
        shift.setOpeningFloat(PmsMoney.require(request.openingFloat(), property.getCurrencyCode()));
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
        HotelProperty property = lockProperty(company, propertyId);
        CashShift shift = cashService.requireOpenShift(property, request.cashShiftId(), username);
        if (paymentRepository.existsByCashShift_IdAndStatus(shift.getId(), PaymentStatus.PENDING))
            throw conflict("Die Kasse enthält noch einen offenen Zahlungsvorgang. Vor dem Abschluss abgleichen.");
        BigDecimal movements = cashMovements(shift);
        BigDecimal expected = PmsMoney.round(shift.getOpeningFloat().add(movements), shift.getProperty().getCurrencyCode());
        BigDecimal actual = PmsMoney.require(request.actualCash(), property.getCurrencyCode());
        shift.setStatus(CashShiftStatus.CLOSED);
        shift.setExpectedCash(expected);
        shift.setActualCash(actual);
        shift.setVariance(PmsMoney.round(actual.subtract(expected), property.getCurrencyCode()));
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
        housekeepingWork.legacyUpdate(company, propertyId, taskId, request);
        return getOperations(company, propertyId, businessDate, null, null);
    }

    private void applyGuest(Company company, GuestProfile guest, UpsertGuestRequest request) {
        guest.setFirstName(required(request.firstName()));
        guest.setLastName(required(request.lastName()));
        guest.setEmail(lower(request.email()));
        guest.setPhone(clean(request.phone()));
        guest.setDateOfBirth(request.dateOfBirth());
        guest.setNationalityCode(upper(request.nationalityCode()));
        guest.setLanguageCode(clean(request.languageCode()) == null ? "de" : lower(request.languageCode()));
        guest.setAddressLine1(clean(request.addressLine1()));
        guest.setPostalCode(clean(request.postalCode()));
        guest.setCity(clean(request.city()));
        guest.setCountryCode(upper(request.countryCode()));
        guest.setVehiclePlate(upper(request.vehiclePlate()));
        guest.setRoomPreferences(clean(request.roomPreferences()));
        guest.setOrganization(request.organizationId() == null ? null
                : organizationRepository.findByIdAndCompany_Id(request.organizationId(), company.getId())
                .filter(PmsOrganization::isActive)
                .orElseThrow(() -> notFound("Firmenkartei nicht gefunden.")));
        guest.setNotes(clean(request.notes()));
        guest.setVip(Boolean.TRUE.equals(request.vip()));
        guest.setPrivateEmail(lower(request.privateEmail()));
        guest.setBusinessEmail(lower(request.businessEmail()));
        guest.setAdditionalEmails(PmsProfileData.encode(PmsProfileData.normalizeEmails(request.additionalEmails())));
        guest.setDietaryNotes(clean(request.dietaryNotes()));
        guest.setVatNumber(clean(request.vatNumber()));
        guest.setBillingOverride(Boolean.TRUE.equals(request.billingOverride()));
        guest.setBillingProfile(PmsProfileData.encode(request.billingProfile()));
        String contactId = clean(request.organizationContactId());
        if (contactId != null && (guest.getOrganization() == null || PmsProfileData.contacts(guest.getOrganization().getContacts())
                .stream().noneMatch(contact -> contactId.equals(contact.id())))) {
            throw badRequest("Der Ansprechpartner muss zur ausgewählten Firma gehören.");
        }
        guest.setOrganizationContactId(contactId);
    }

    private void applyRatePlan(RatePlan ratePlan,
                               UpsertRatePlanRequest request,
                               HotelProperty property,
                               boolean creating) {
        ratePlan.setCode(code(request.code()));
        ratePlan.setName(required(request.name()));
        ratePlan.setCurrencyCode(property.getCurrencyCode());
        ratePlan.setNightlyRate(PmsMoney.require(request.nightlyRate(), property.getCurrencyCode()));
        ratePlan.setMinStay(request.minStay());
        ratePlan.setBreakfastIncluded(request.breakfastIncluded());
        ratePlan.setRefundable(request.refundable());
        if (request.maxStay() != null && request.maxStay() < request.minStay()) {
            throw badRequest("Der Höchstaufenthalt darf den Mindestaufenthalt nicht unterschreiten.");
        }
        if (request.minAdvanceDays() != null && request.maxAdvanceDays() != null
                && request.minAdvanceDays() > request.maxAdvanceDays()) {
            throw badRequest("Die maximale Vorausbuchung muss mindestens der minimalen entsprechen.");
        }
        if (request.validFrom() != null && request.validTo() != null && request.validFrom().isAfter(request.validTo())
                || request.bookingFrom() != null && request.bookingTo() != null && request.bookingFrom().isAfter(request.bookingTo())) {
            throw badRequest("Das Ende eines Ratenzeitraums muss am oder nach dem Beginn liegen.");
        }
        BigDecimal breakfast = request.breakfastAmount() == null ? BigDecimal.ZERO : PmsMoney.require(request.breakfastAmount(), property.getCurrencyCode());
        if (breakfast.signum() < 0 || breakfast.compareTo(ratePlan.getNightlyRate()) > 0
                || !request.breakfastIncluded() && breakfast.signum() > 0) {
            throw badRequest("Der Frühstücksanteil muss im Nachtpreis enthalten sein und Frühstück eingeschaltet sein.");
        }
        boolean inclusive = request.taxIncluded() == null ? ratePlan.isTaxIncluded() : request.taxIncluded();
        if (!inclusive && request.vatRate() == null) {
            throw badRequest("Für Nettopreise ist ein Steuersatz erforderlich (auch bei 0 %).");
        }
        if (breakfast.signum() > 0 && request.breakfastVatRate() == null) {
            throw badRequest("Für den getrennten Frühstücksanteil ist ein eigener Steuersatz erforderlich.");
        }
        int includedAdults = request.includedAdults() == null ? ratePlan.getIncludedAdults() : request.includedAdults();
        if (includedAdults > ratePlan.getRoomType().getMaxOccupancy()) {
            throw badRequest("Die enthaltenen Erwachsenen überschreiten die Zimmerkapazität.");
        }
        ratePlan.setVatRate(request.vatRate());
        ratePlan.setTaxIncluded(inclusive);
        ratePlan.setBreakfastAmount(breakfast);
        ratePlan.setBreakfastVatRate(request.breakfastVatRate());
        ratePlan.setValidFrom(request.validFrom());
        ratePlan.setValidTo(request.validTo());
        ratePlan.setBookingFrom(request.bookingFrom());
        ratePlan.setBookingTo(request.bookingTo());
        ratePlan.setMaxStay(request.maxStay());
        ratePlan.setMinAdvanceDays(request.minAdvanceDays());
        ratePlan.setMaxAdvanceDays(request.maxAdvanceDays());
        ratePlan.setIncludedAdults(includedAdults);
        ratePlan.setExtraAdultRate(request.extraAdultRate() == null ? BigDecimal.ZERO : PmsMoney.require(request.extraAdultRate(), property.getCurrencyCode()));
        ratePlan.setChildRate(request.childRate() == null ? BigDecimal.ZERO : PmsMoney.require(request.childRate(), property.getCurrencyCode()));
        ratePlan.setCancellationDeadlineHours(request.cancellationDeadlineHours());
        ratePlan.setCancellationFeePercent(request.cancellationFeePercent());
        ratePlan.setDepositPercent(request.depositPercent());
        ratePlan.setNoShowFeePercent(request.noShowFeePercent());
        ratePlan.setPolicyFeeTaxRate(request.policyFeeTaxRate());
        ratePlan.setDepositDueDaysBeforeArrival(request.depositDueDaysBeforeArrival());
        if ((request.cancellationFeePercent() != null && request.cancellationFeePercent().signum() > 0
                || request.noShowFeePercent() != null && request.noShowFeePercent().signum() > 0) && request.policyFeeTaxRate() == null) {
            throw badRequest("Für Storno-/No-Show-Gebühren muss der Steuersatz ausdrücklich angegeben werden (0 bei steuerfrei).");
        }
        ratePlan.setPaymentDueDays(request.paymentDueDays());
        ratePlan.setCancellationPolicy(clean(request.cancellationPolicy()));
        ratePlan.setPaymentPolicy(clean(request.paymentPolicy()));
        ratePlan.setNotes(clean(request.notes()));
        ratePlan.setOrganization(request.organizationId() == null ? null : organizationRepository
                .findByIdAndCompany_Id(request.organizationId(), property.getCompany().getId())
                .filter(PmsOrganization::isActive).orElseThrow(() -> notFound("Firma für die Vertragsrate nicht gefunden.")));
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
        if (!guest.isActive()) {
            throw conflict("Die Gästekartei wurde zusammengeführt und kann nicht mehr gebucht werden.");
        }
        boolean guestChanged = reservation.getGuest() == null
                || !reservation.getGuest().getId().equals(guest.getId());
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
                    excludeReservationId,
                    reservation.getGroupBooking()==null?null:reservation.getGroupBooking().getId()
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
        if (!eligibleForRate(ratePlan, guest)) {
            throw conflict("Die Firmenrate ist nur für Gäste der zugeordneten Firma buchbar.");
        }
        LocalDate bookingDate = reservation.getCreatedAt() == null ? today(ratePlan.getProperty())
                : reservation.getCreatedAt().toLocalDate();
        Quote quote = quote(ratePlan, request.arrivalDate(), request.departureDate(), request.adults(), request.children(), bookingDate);
        if (quote.restriction() != null) {
            throw conflict(quote.restriction());
        }
        reservation.setGuest(guest);
        reservation.setRoomType(roomType);
        reservation.setRoom(room);
        if (reservation.getId() == null || reservation.getRatePlan() == null || !Objects.equals(reservation.getRatePlan().getId(), ratePlan.getId())) {
            PmsReservationPolicyService.snapshot(reservation, ratePlan);
        }
        reservation.setRatePlan(ratePlan);
        reservation.setArrivalDate(request.arrivalDate());
        reservation.setDepartureDate(request.departureDate());
        reservation.setAdults(request.adults());
        reservation.setChildren(request.children());
        if (request.childAges() != null && !request.childAges().isEmpty()
                && request.childAges().size() != request.children()) {
            throw badRequest("Für jedes Kind muss genau ein Alter erfasst sein.");
        }
        reservation.setChildAges(serializeChildAges(request.childAges()));
        if (guestChanged || reservation.getGuestPreferenceSnapshot() == null) {
            reservation.setGuestPreferenceSnapshot(clean(guest.getRoomPreferences()));
        }
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
        Long groupId=excludeReservationId==null?null:reservationRepository.findById(excludeReservationId)
                .map(Reservation::getGroupBooking).map(GroupBooking::getId).orElse(null);
        ensureCapacity(propertyId,roomTypeId,arrival,departure,excludeReservationId,groupId);
    }

    private void ensureCapacity(Long propertyId,Long roomTypeId,LocalDate arrival,LocalDate departure,
                                Long excludeReservationId,Long groupId) {
        Map<LocalDate,Long> heldByDate=groupInventory.heldByNight(propertyId,roomTypeId,arrival,departure,groupId);
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
            sold += heldByDate.getOrDefault(date,0L);
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

    private boolean eligibleForRate(RatePlan ratePlan, GuestProfile guest) {
        return eligibleForOrganization(ratePlan, guest == null ? null : guest.getOrganization());
    }

    private boolean eligibleForOrganization(RatePlan ratePlan, PmsOrganization organization) {
        return ratePlan.getOrganization() == null || organization != null
                && ratePlan.getOrganization().isActive()
                && ratePlan.getOrganization().getId().equals(organization.getId());
    }

    private Quote quote(RatePlan ratePlan, LocalDate arrival, LocalDate departure, int adults, int children,
                         LocalDate bookingDate) {
        validateStay(arrival, departure);
        List<RateOverride> overrides =
                rateOverrideRepository.findAllByRatePlan_IdAndStayDateBetweenOrderByStayDateAsc(
                        ratePlan.getId(),
                        arrival,
                        departure
                );
        return quote(ratePlan, arrival, departure, overrides, adults, children, bookingDate);
    }

    private Quote quote(RatePlan ratePlan, LocalDate arrival, LocalDate departure,
                        List<RateOverride> overrides, int adults, int children) {
        return quote(ratePlan, arrival, departure, overrides, adults, children, today(ratePlan.getProperty()));
    }

    private Quote quote(RatePlan ratePlan, LocalDate arrival, LocalDate departure,
                        List<RateOverride> overrides, int adults, int children, LocalDate bookingDate) {
        validateStay(arrival, departure);
        String restriction = PmsRatePricing.restriction(ratePlan, arrival, departure, bookingDate);
        if (restriction != null) return new Quote(BigDecimal.ZERO, restriction);
        long nights = ChronoUnit.DAYS.between(arrival, departure);
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
            }
            try {
                total = total.add(PmsRatePricing.night(ratePlan,
                        override == null ? ratePlan.getNightlyRate() : override.getPrice(), adults, children).total());
            } catch (IllegalArgumentException ex) {
                return new Quote(BigDecimal.ZERO, ex.getMessage());
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
        return new Quote(PmsMoney.round(total, ratePlan.getCurrencyCode()), null);
    }

    private InventorySummary summarizeInventory(Long roomTypeId,
                                                LocalDate arrival,
                                                LocalDate departure,
                                                List<Room> rooms,
                                                List<Reservation> reservations,
                                                List<RoomBlock> blocks) {
        Set<Long> sellableRoomIds = rooms.stream()
                .filter(Room::isActive)
                .filter(room -> room.getOperationalStatus() == RoomOperationalStatus.IN_SERVICE)
                .filter(room -> room.getRoomType().getId().equals(roomTypeId))
                .map(Room::getId)
                .collect(Collectors.toSet());
        long maximumSold = 0;
        long minimumAvailable = sellableRoomIds.size();
        Map<LocalDate,Long> heldByDate=rooms.isEmpty()?Map.of():groupInventory.heldByNight(rooms.get(0).getProperty().getId(),roomTypeId,arrival,departure,null);
        for (LocalDate date = arrival; date.isBefore(departure); date = date.plusDays(1)) {
            LocalDate stayDate = date;
            long sold = reservations.stream()
                    .filter(reservation -> reservation.getRoomSegments().isEmpty()
                            ? reservation.getRoomType().getId().equals(roomTypeId)
                            : reservation.getRoomSegments().stream().anyMatch(s -> s.getRoom().getRoomType().getId().equals(roomTypeId)
                                && !stayDate.isBefore(s.getStartDate()) && stayDate.isBefore(s.getEndDate())))
                    .filter(reservation -> !NON_INVENTORY_STATUSES.contains(reservation.getStatus()))
                    .filter(reservation -> reservation.getArrivalDate().isBefore(stayDate.plusDays(1))
                            && reservation.getDepartureDate().isAfter(stayDate))
                    .count();
            long blocked = blocks.stream()
                    .filter(block -> block.getStatus() == RoomBlockStatus.ACTIVE)
                    .filter(block -> INVENTORY_BLOCKING_ROOM_BLOCK_TYPES.contains(block.getType()))
                    .filter(block -> sellableRoomIds.contains(block.getRoom().getId()))
                    .filter(block -> block.getStartDate().isBefore(stayDate.plusDays(1))
                            && block.getEndDate().isAfter(stayDate))
                    .map(block -> block.getRoom().getId())
                    .distinct()
                    .count();
            long held=heldByDate.getOrDefault(date,0L);
            maximumSold = Math.max(maximumSold, sold);
            minimumAvailable = Math.min(minimumAvailable,
                    Math.max(0, sellableRoomIds.size() - blocked - sold - held));
        }
        return new InventorySummary(sellableRoomIds.size(), maximumSold, minimumAvailable);
    }

    private void createFolioWithRoomCharges(Reservation reservation) {
        Folio folio = new Folio();
        folio.setReservation(reservation);
        folio.setCurrencyCode(reservation.getCurrencyCode());
        folio.setStatus(FolioStatus.OPEN);
        folio.setLabel("Hauptkonto");
        folio.setOrganization(reservation.getGuest().getOrganization());
        folioRepository.save(folio);
        saveRoomChargeItems(folio, reservation);
        groupRouting.route(reservation);
    }

    private void refreshRoomCharges(Reservation reservation) {
        Folio folio = folioRepository.findFirstByReservation_IdOrderByIdAsc(reservation.getId())
                .orElseThrow(() -> conflict("Zur Reservierung fehlt das Gastkonto."));
        List<FolioItem> existing = folioItemRepository.findAllByFolio_Reservation_IdAndRateGeneratedTrueOrderByServiceDateAscIdAsc(reservation.getId());
        Map<String, FolioItem> byKey = new LinkedHashMap<>();
        for (FolioItem item : existing) {
            String key = item.getServiceDate() + ":" + item.getType();
            if (byKey.putIfAbsent(key, item) != null) {
                throw conflict("Doppelte automatisch erzeugte Leistungen müssen vor der Aufenthaltsänderung korrigiert werden.");
            }
        }
        Map<Long, Map<LocalDate, RateOverride>> overridesByRate = new HashMap<>();
        for (LocalDate date = reservation.getArrivalDate(); date.isBefore(reservation.getDepartureDate()); date = date.plusDays(1)) {
            LocalDate day = date;
            RatePlan rate = reservation.getRoomSegments().stream().filter(s -> !day.isBefore(s.getStartDate()) && day.isBefore(s.getEndDate()))
                    .map(ReservationRoomSegment::getRatePlan).findFirst().orElse(reservation.getRatePlan());
            Map<LocalDate, RateOverride> overrides = overridesByRate.computeIfAbsent(rate.getId(), id -> rateOverrideRepository
                    .findAllByRatePlan_IdAndStayDateBetweenOrderByStayDateAsc(id, reservation.getArrivalDate(), reservation.getDepartureDate())
                    .stream().collect(Collectors.toMap(RateOverride::getStayDate, Function.identity())));
            BigDecimal nightly = Optional.ofNullable(overrides.get(date)).map(RateOverride::getPrice).orElse(rate.getNightlyRate());
            PmsRatePricing.NightPrice price = PmsRatePricing.night(rate, nightly, reservation.getAdults(), reservation.getChildren());
            reconcileRateCharge(folio, byKey.remove(date + ":" + FolioItemType.ROOM), date, FolioItemType.ROOM,
                    "Übernachtung " + rate.getRoomType().getName(), price.accommodation(), rate.getVatRate());
            if (price.breakfast().signum() > 0) reconcileRateCharge(folio, byKey.remove(date + ":" + FolioItemType.BREAKFAST), date,
                    FolioItemType.BREAKFAST, "Frühstück · " + rate.getName(), price.breakfast(), rate.getBreakfastVatRate());
        }
        for (FolioItem obsolete : byKey.values()) {
            assertRateChargeMutable(obsolete);
            folioItemRepository.delete(obsolete);
        }
        groupRouting.route(reservation);
    }

    private void assertRateChargeMutable(FolioItem item) {
        if (item.getFolio().getStatus() != FolioStatus.OPEN || invoiceLineRepository.existsBySourceItem_Id(item.getId())) {
            throw conflict("Bereits fakturierte oder abgeschlossene Leistungen benötigen eine Rechnungskorrektur.");
        }
        financialPeriods.assertPostingOpen(item.getFolio().getReservation().getProperty(), item.getServiceDate());
    }

    private void reconcileRateCharge(Folio defaultFolio, FolioItem item, LocalDate date, FolioItemType type,
                                     String description, BigDecimal gross, BigDecimal taxRate) {
        if (item == null) {
            if (defaultFolio.getStatus() != FolioStatus.OPEN) throw conflict("Ein geschlossenes Gastkonto kann nicht erweitert werden.");
            financialPeriods.assertPostingOpen(defaultFolio.getReservation().getProperty(), date);
            saveRateChargeItem(defaultFolio, date, type, description, gross, taxRate);
            return;
        }
        boolean sameTax = item.getTaxRate() == null ? taxRate == null : taxRate != null && item.getTaxRate().compareTo(taxRate) == 0;
        if (item.getTotalAmount().compareTo(gross) == 0 && sameTax) return;
        assertRateChargeMutable(item);
        item.setDescription(description);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(gross);
        item.setTotalAmount(gross);
        item.setTaxRate(taxRate);
        folioItemRepository.save(item);
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
            RatePlan rate = reservation.getRatePlan();
            PmsRatePricing.NightPrice price = PmsRatePricing.night(rate, nightly, reservation.getAdults(), reservation.getChildren());
            saveRateChargeItem(folio, date, FolioItemType.ROOM, "Übernachtung " + reservation.getRoomType().getName(),
                    price.accommodation(), rate.getVatRate());
            if (price.breakfast().signum() > 0) {
                saveRateChargeItem(folio, date, FolioItemType.BREAKFAST, "Frühstück · " + rate.getName(),
                        price.breakfast(), rate.getBreakfastVatRate());
            }
            date = date.plusDays(1);
        }
    }

    private void saveRateChargeItem(Folio folio, LocalDate date, FolioItemType type, String description,
                                    BigDecimal gross, BigDecimal taxRate) {
        FolioItem item = new FolioItem();
        item.setFolio(folio);
        item.setServiceDate(date);
        item.setType(type);
        item.setDescription(description);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(gross);
        item.setTotalAmount(gross);
        item.setTaxRate(taxRate);
        item.setTaxIncluded(true);
        item.setRateGenerated(true);
        folioItemRepository.save(item);
    }

    private void markRoomDirty(HotelProperty property, Room room, LocalDate serviceDate) {
        room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        roomRepository.save(room);
        housekeepingWork.departure(property, room, serviceDate);
    }

    private PmsOperationsResponse.GuestView toGuestView(GuestProfile guest) {
        return new PmsOperationsResponse.GuestView(
                guest.getId(),
                guest.getReferenceCode(),
                guest.getFirstName(),
                guest.getLastName(),
                guest.getEmail(),
                guest.getPhone(),
                guest.getDateOfBirth(),
                guest.getNationalityCode(),
                guest.getLanguageCode(),
                guest.getAddressLine1(),
                guest.getPostalCode(),
                guest.getCity(),
                guest.getCountryCode(),
                guest.getVehiclePlate(),
                guest.getRoomPreferences(),
                guest.getOrganization() == null ? null : guest.getOrganization().getId(),
                guest.getOrganization() == null ? null : guest.getOrganization().getName(),
                guest.getNotes(),
                guest.isVip(),
                guest.isActive(),
                guest.getMergedInto() == null ? null : guest.getMergedInto().getId(),
                guest.getPrivateEmail(), guest.getBusinessEmail(), PmsProfileData.emails(guest.getAdditionalEmails()),
                guest.getDietaryNotes(), guest.getVatNumber(), guest.getOrganizationContactId(), guest.isBillingOverride(),
                PmsProfileData.billing(guest.getBillingProfile())
        );
    }

    private PmsOperationsResponse.OrganizationSummaryView toOrganizationSummaryView(PmsOrganization organization) {
        return new PmsOperationsResponse.OrganizationSummaryView(
                organization.getId(), organization.getReferenceCode(), organization.getName(),
                organization.getAddressLine1(), organization.getPostalCode(), organization.getCity(),
                organization.getCountryCode(), organization.isMasterRecord(),
                organization.getParentOrganization() == null ? null : organization.getParentOrganization().getId(),
                organization.isActive(), PmsProfileData.contacts(organization.getContacts()), PmsProfileData.billing(organization.getBillingProfile()));
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
                ratePlan.isActive(),
                ratePlan.getVatRate(), ratePlan.isTaxIncluded(), ratePlan.getBreakfastAmount(), ratePlan.getBreakfastVatRate(),
                ratePlan.getValidFrom(), ratePlan.getValidTo(), ratePlan.getBookingFrom(), ratePlan.getBookingTo(),
                ratePlan.getMaxStay(), ratePlan.getMinAdvanceDays(), ratePlan.getMaxAdvanceDays(), ratePlan.getIncludedAdults(),
                ratePlan.getExtraAdultRate(), ratePlan.getChildRate(), ratePlan.getCancellationDeadlineHours(),
                ratePlan.getCancellationFeePercent(), ratePlan.getDepositPercent(), ratePlan.getPaymentDueDays(),
                ratePlan.getCancellationPolicy(), ratePlan.getPaymentPolicy(), ratePlan.getNotes(),
                ratePlan.getOrganization() == null ? null : ratePlan.getOrganization().getId(),
                ratePlan.getOrganization() == null ? null : ratePlan.getOrganization().getName(),
                ratePlan.getNoShowFeePercent(), ratePlan.getPolicyFeeTaxRate(), ratePlan.getDepositDueDaysBeforeArrival()
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
        return toReservationView(reservation,
                reservationStatusHistoryRepository.findAllByReservation_IdOrderByChangedAtDesc(reservation.getId()));
    }

    private PmsOperationsResponse.ReservationView toReservationView(Reservation reservation,
                                                                  List<ReservationStatusHistory> histories) {
        Room displayedRoom = roomOn(reservation, today(reservation.getProperty()));
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
                displayedRoom == null ? null : displayedRoom.getId(),
                displayedRoom == null ? null : displayedRoom.getNumber(),
                reservation.getRatePlan().getId(),
                reservation.getRatePlan().getName(),
                reservation.getArrivalDate(),
                reservation.getDepartureDate(),
                reservation.getAdults(),
                reservation.getChildren(),
                parseChildAges(reservation.getChildAges()),
                reservation.getStatus(),
                reservation.getSource(),
                reservation.getGuaranteeStatus(),
                reservation.getHoldUntil(),
                reservation.getTotalAmount(),
                reservation.getCurrencyCode(),
                reservation.getNotes(),
                reservation.getGuestPreferenceSnapshot(),
                reservation.getCheckedInAt(),
                reservation.getCheckedOutAt(),
                reservation.getCancelledAt(),
                reservation.getNoShowAt(),
                reservation.getCancellationReason(),
                histories.stream()
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

    private PmsOperationsResponse.RoomStateView toRoomStateView(Room room, PmsOperationsResponse.ReservationView currentReservation) {
        return new PmsOperationsResponse.RoomStateView(
                room.getId(),
                room.getRoomType().getId(),
                room.getRoomType().getName(),
                room.getNumber(),
                room.getFloor(),
                room.getFeatures(),
                room.getOperationalStatus(),
                room.getHousekeepingStatus(),
                currentReservation
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
                task.getCompletedAt(),
                task.getVersion(), task.getWorkType(), task.getWorkStatus()
        );
    }

    private PmsOperationsResponse.FolioView toFolioView(Folio folio) {
        List<FolioItem> items = folioItemRepository.findAllByFolio_IdOrderByServiceDateAscIdAsc(folio.getId());
        List<Payment> payments = paymentRepository.findAllByFolio_IdOrderByReceivedAtAsc(folio.getId());
        return toFolioView(folio, items, payments);
    }

    private PmsOperationsResponse.FolioView toFolioView(Folio folio, List<FolioItem> items,
                                                         List<Payment> payments) {
        Set<Long> invoicedIds = items.isEmpty() ? Set.of() : new HashSet<>(invoiceLineRepository.findAllocatedSourceIds(items.stream().map(FolioItem::getId).toList()));
        return toFolioView(folio, items, payments, invoicedIds);
    }

    private PmsOperationsResponse.FolioView toFolioView(Folio folio, List<FolioItem> items,
                                                      List<Payment> payments, Set<Long> invoicedIds) {
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
                PmsMoney.round(charges, folio.getReservation().getCurrencyCode()),
                PmsMoney.round(paid, folio.getReservation().getCurrencyCode()),
                PmsMoney.round(charges.subtract(paid), folio.getReservation().getCurrencyCode()),
                items.stream().map(item -> new PmsOperationsResponse.FolioItemView(
                        item.getId(),
                        item.getServiceDate(),
                        item.getType(),
                        item.getDescription(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalAmount(),
                        item.getTaxRate(),
                        invoicedIds.contains(item.getId())
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
                        payment.getVoidedBy(),
                        payment.getCashShift() == null ? null : payment.getCashShift().getId(),
                        payment.getProviderTransactionId(), payment.getProviderStatus(), payment.getRefundRequestId()
                )).toList(),
                folio.isGroupMaster(),folio.getGroupBooking()==null?null:folio.getGroupBooking().getId()
        );
    }

    private PmsOperationsResponse.CashShiftView toCashShiftView(CashShift shift) {
        BigDecimal movements = cashMovements(shift);
        BigDecimal expected = shift.getExpectedCash() == null
                ? PmsMoney.round(shift.getOpeningFloat().add(movements), shift.getProperty().getCurrencyCode())
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
                shift.getNotes(), shift.getRegisterCode(), shift.getOutletCode()
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
        // Before V28 only one shift existed per hotel and payments had no shift FK.
        BigDecimal legacy = paymentRepository.findAllByFolio_Reservation_Property_IdAndMethodAndStatusAndReceivedAtGreaterThanEqual(
                shift.getProperty().getId(), PaymentMethod.CASH, PaymentStatus.POSTED, shift.getOpenedAt()).stream()
                .filter(p -> p.getCashShift() == null && (shift.getClosedAt() == null || !p.getReceivedAt().isAfter(shift.getClosedAt())))
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return PmsMoney.round(paymentRepository
                .findAllByCashShift_IdAndMethodAndStatus(shift.getId(), PaymentMethod.CASH, PaymentStatus.POSTED)
                .stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).add(posTickets.sumCashByShift(shift.getId())).add(legacy), shift.getProperty().getCurrencyCode());
    }

    @Transactional(readOnly = true)
    public List<PmsOperationsResponse.CashShiftView> getCashShifts(Company company, Long propertyId) {
        requireProperty(company, propertyId);
        return cashShiftRepository.findAllByProperty_IdOrderByOpenedAtDesc(propertyId).stream().limit(100).map(this::toCashShiftView).toList();
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

    private Reservation requireLockedReservation(Company company,Long reservationId) {
        Long propertyId=reservationRepository.findPropertyIdForTenant(reservationId,company.getId())
                .orElseThrow(()->notFound("Reservierung nicht gefunden."));
        lockProperty(company,propertyId);
        Reservation reservation=requireReservation(company,reservationId);
        // Bulk callers may have loaded the member before waiting for the hotel lock.
        lifecycleEntityManager.refresh(reservation,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return reservation;
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

    private void ensureGuestReference(GuestProfile guest) {
        if (guest.getReferenceCode() == null && guest.getId() != null) {
            guest.setReferenceCode("GK%06d".formatted(guest.getId()));
        }
    }

    private String serializeChildAges(List<Integer> childAges) {
        if (childAges == null || childAges.isEmpty()) {
            return null;
        }
        return childAges.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Integer> parseChildAges(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return List.of();
        }
        try {
            return Arrays.stream(cleaned.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .map(Integer::valueOf)
                    .toList();
        } catch (NumberFormatException ignored) {
            return List.of();
        }
    }

    private void applyGuestMergeFields(GuestProfile source, GuestProfile target, Set<String> fields) {
        for (String field : fields) {
            switch (field) {
                case "firstName" -> target.setFirstName(source.getFirstName());
                case "lastName" -> target.setLastName(source.getLastName());
                case "email" -> target.setEmail(source.getEmail());
                case "privateEmail" -> target.setPrivateEmail(source.getPrivateEmail());
                case "businessEmail" -> target.setBusinessEmail(source.getBusinessEmail());
                case "additionalEmails" -> target.setAdditionalEmails(source.getAdditionalEmails());
                case "dietaryNotes" -> target.setDietaryNotes(source.getDietaryNotes());
                case "vatNumber" -> target.setVatNumber(source.getVatNumber());
                case "billingProfile" -> { target.setBillingProfile(source.getBillingProfile()); target.setBillingOverride(source.isBillingOverride()); }
                case "phone" -> target.setPhone(source.getPhone());
                case "dateOfBirth" -> target.setDateOfBirth(source.getDateOfBirth());
                case "nationalityCode" -> target.setNationalityCode(source.getNationalityCode());
                case "languageCode" -> target.setLanguageCode(source.getLanguageCode());
                case "addressLine1" -> target.setAddressLine1(source.getAddressLine1());
                case "postalCode" -> target.setPostalCode(source.getPostalCode());
                case "city" -> target.setCity(source.getCity());
                case "countryCode" -> target.setCountryCode(source.getCountryCode());
                case "vehiclePlate" -> target.setVehiclePlate(source.getVehiclePlate());
                case "roomPreferences" -> target.setRoomPreferences(source.getRoomPreferences());
                case "organization" -> target.setOrganization(source.getOrganization());
                case "notes" -> target.setNotes(source.getNotes());
                case "vip" -> target.setVip(source.isVip());
                default -> throw badRequest("Unbekanntes Feld für die Zusammenführung: " + field);
            }
        }
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

    private record InventorySummary(long total, long maximumSold, long minimumAvailable) {
    }
}
