package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsRoomPlanFilter;
import com.chrono.chrono.dto.pms.PmsRoomPlanResponse;
import com.chrono.chrono.dto.pms.PmsRoomPlanResponse.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Bounded, tenant-scoped read model for the room timeline. End dates are always exclusive. */
@Service
@Transactional(readOnly = true)
public class PmsRoomPlanService {
    private static final List<ReservationStatus> OCCUPYING = List.of(
            ReservationStatus.TENTATIVE, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN);
    private static final List<ReservationStatus> VISIBLE = List.of(
            ReservationStatus.TENTATIVE, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT);
    private final EntityManager em;
    private final HotelPropertyRepository properties;

    public PmsRoomPlanService(EntityManager em, HotelPropertyRepository properties) {
        this.em = em;
        this.properties = properties;
    }

    public PmsRoomPlanResponse getRoomPlan(Company company, Long propertyId, PmsRoomPlanFilter f) {
        HotelProperty property = properties.findByIdAndCompany_Id(propertyId, company.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel nicht gefunden."));
        validate(f);
        LocalDate today = LocalDate.now(ZoneId.of(property.getTimezone()));
        LocalDate from = f.from() == null ? today : f.from();
        LocalDate to = from.plusDays(f.days());
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Room> countRoot = countQuery.from(Room.class);
        countQuery.select(cb.count(countRoot)).where(predicates(cb, countQuery, countRoot, company.getId(), propertyId, from, to, f));
        long total = em.createQuery(countQuery).getSingleResult();
        int page = total == 0 ? 0 : (int) Math.min(f.page(), (total - 1) / f.size());
        CriteriaQuery<Room> query = cb.createQuery(Room.class);
        Root<Room> room = query.from(Room.class);
        room.fetch("roomType", JoinType.INNER);
        query.select(room).where(predicates(cb, query, room, company.getId(), propertyId, from, to, f))
                .orderBy(cb.asc(room.get("floor")), cb.asc(cb.length(room.get("number"))), cb.asc(room.get("number")), cb.asc(room.get("id")));
        List<Room> pageRooms = em.createQuery(query).setFirstResult(page * f.size()).setMaxResults(f.size()).getResultList();
        List<Long> ids = pageRooms.stream().map(Room::getId).toList();
        List<ReservationView> reservations = new ArrayList<>(ids.isEmpty() ? List.of() : em.createQuery("""
                select r from Reservation r join fetch r.guest
                where r.property.id = :propertyId and r.property.company.id = :companyId
                and r.room.id in :ids and r.status in :statuses
                and r.roomSegments is empty
                and r.arrivalDate < :to and r.departureDate > :from
                order by r.arrivalDate, r.id
                """, Reservation.class).setParameter("propertyId", propertyId).setParameter("companyId", company.getId())
                .setParameter("ids", ids).setParameter("statuses", VISIBLE).setParameter("to", to).setParameter("from", from)
                .getResultList().stream().map(this::reservationView).toList());
        if (!ids.isEmpty()) {
            reservations.addAll(em.createQuery("""
                    select s from ReservationRoomSegment s join fetch s.reservation r join fetch r.guest
                    join fetch s.room room join fetch room.roomType join fetch s.ratePlan
                    where r.property.id = :propertyId and r.property.company.id = :companyId
                    and s.room.id in :ids and r.status in :statuses
                    and s.startDate < :to and s.endDate > :from order by s.startDate, s.id
                    """, ReservationRoomSegment.class).setParameter("propertyId", propertyId)
                    .setParameter("companyId", company.getId()).setParameter("ids", ids)
                    .setParameter("statuses", VISIBLE).setParameter("to", to).setParameter("from", from)
                    .getResultList().stream().map(s -> reservationView(s.getReservation(), s)).toList());
        }
        List<BlockView> blocks = ids.isEmpty() ? List.of() : em.createQuery("""
                select b from RoomBlock b where b.property.id = :propertyId and b.property.company.id = :companyId
                and b.room.id in :ids and b.status = :status and b.startDate < :to and b.endDate > :from
                order by b.startDate, b.id
                """, RoomBlock.class).setParameter("propertyId", propertyId).setParameter("companyId", company.getId())
                .setParameter("ids", ids).setParameter("status", RoomBlockStatus.ACTIVE).setParameter("to", to).setParameter("from", from)
                .getResultList().stream().map(b -> new BlockView(b.getId(), b.getRoom().getId(), b.getType(), b.getStartDate(), b.getEndDate(), b.getReason())).toList();
        return new PmsRoomPlanResponse(propertyId, property.getTimezone(), today, from, to, page, f.size(), total,
                pageRooms.stream().map(this::roomView).toList(), reservations, blocks, filters(propertyId, company.getId()));
    }

    private Predicate[] predicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Room> r, Long companyId,
                                   Long propertyId, LocalDate from, LocalDate to, PmsRoomPlanFilter f) {
        List<Predicate> terms = new ArrayList<>();
        terms.add(cb.equal(r.get("property").get("id"), propertyId));
        terms.add(cb.equal(r.get("property").get("company").get("id"), companyId));
        if (!f.includeInactive()) terms.add(cb.isTrue(r.get("active")));
        if (f.roomTypeId() != null) terms.add(cb.equal(r.get("roomType").get("id"), f.roomTypeId()));
        if (hasText(f.floor())) terms.add(cb.equal(r.get("floor"), f.floor()));
        if (hasText(f.bedType())) terms.add(cb.equal(r.get("roomType").get("bedType"), f.bedType()));
        if (hasText(f.housekeepingSection())) terms.add(cb.equal(r.get("housekeepingSection"), f.housekeepingSection()));
        if (f.housekeepingStatus() != null) terms.add(cb.equal(r.get("housekeepingStatus"), f.housekeepingStatus()));
        if (f.operationalStatus() != null) terms.add(cb.equal(r.get("operationalStatus"), f.operationalStatus()));
        if (f.guests() != null) terms.add(cb.ge(r.get("roomType").get("maxOccupancy"), f.guests()));
        if (hasText(f.search())) {
            String search = "%" + escapeLike(f.search().trim().toLowerCase(Locale.ROOT)) + "%";
            terms.add(cb.or(cb.like(cb.lower(r.get("number")), search, '!'), cb.like(cb.lower(r.get("name")), search, '!'),
                    cb.like(cb.lower(r.get("features")), search, '!'), cb.like(cb.lower(r.get("roomType").get("name")), search, '!')));
        }
        if (f.features() != null) {
            // Match complete maintained feature tokens, never "Badewanne" inside "keine Badewanne".
            Expression<String> normalized = cb.lower(cb.coalesce(r.get("features"), ""));
            for (String delimiter : List.of(";", "\r", "\n")) normalized = replace(cb, normalized, delimiter, ",");
            normalized = replace(cb, replace(cb, normalized, " ", ""), "\t", "");
            normalized = cb.concat(cb.concat(",", normalized), ",");
            for (String feature : f.features()) {
                String token = feature.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
                terms.add(cb.like(normalized, "%," + escapeLike(token) + ",%", '!'));
            }
        }
        if (f.onlyAvailable()) {
            terms.add(cb.isTrue(r.get("active")));
            terms.add(cb.equal(r.get("operationalStatus"), RoomOperationalStatus.IN_SERVICE));
            Subquery<Long> occupied = query.subquery(Long.class);
            Root<Reservation> stay = occupied.from(Reservation.class);
            occupied.select(stay.get("id")).where(cb.equal(stay.get("room").get("id"), r.get("id")),
                    cb.isEmpty(stay.get("roomSegments")),
                    cb.equal(stay.get("property").get("id"), propertyId), stay.get("status").in(OCCUPYING),
                    cb.lessThan(stay.get("arrivalDate"), to), cb.greaterThan(stay.get("departureDate"), from));
            terms.add(cb.not(cb.exists(occupied)));
            Subquery<Long> segmented = query.subquery(Long.class);
            Root<ReservationRoomSegment> segment = segmented.from(ReservationRoomSegment.class);
            segmented.select(segment.get("id")).where(cb.equal(segment.get("room").get("id"), r.get("id")),
                    cb.equal(segment.get("reservation").get("property").get("id"), propertyId),
                    segment.get("reservation").get("status").in(OCCUPYING),
                    cb.lessThan(segment.get("startDate"), to), cb.greaterThan(segment.get("endDate"), from));
            terms.add(cb.not(cb.exists(segmented)));
            Subquery<Long> blocked = query.subquery(Long.class);
            Root<RoomBlock> block = blocked.from(RoomBlock.class);
            blocked.select(block.get("id")).where(cb.equal(block.get("room").get("id"), r.get("id")),
                    cb.equal(block.get("property").get("id"), propertyId), cb.equal(block.get("status"), RoomBlockStatus.ACTIVE),
                    block.get("type").in(RoomBlockType.OUT_OF_ORDER, RoomBlockType.OWNER_USE),
                    cb.lessThan(block.get("startDate"), to), cb.greaterThan(block.get("endDate"), from));
            terms.add(cb.not(cb.exists(blocked)));
        }
        return terms.toArray(Predicate[]::new);
    }

    private Filters filters(Long propertyId, Long companyId) {
        List<TypeOption> types = em.createQuery("select t from RoomType t where t.property.id = :propertyId and t.property.company.id = :companyId order by t.sortOrder, t.name", RoomType.class)
                .setParameter("propertyId", propertyId).setParameter("companyId", companyId).getResultList()
                .stream().map(t -> new TypeOption(t.getId(), t.getName())).toList();
        TreeSet<String> features = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        distinctRoomValues("features", propertyId, companyId).forEach(s -> Arrays.stream(s.split("[,;\\r\\n]+"))
                .map(String::trim).filter(v -> !v.isEmpty()).forEach(features::add));
        List<String> beds = em.createQuery("select distinct t.bedType from RoomType t where t.property.id = :propertyId and t.property.company.id = :companyId and t.bedType is not null order by t.bedType", String.class)
                .setParameter("propertyId", propertyId).setParameter("companyId", companyId).getResultList().stream().filter(this::hasText).toList();
        return new Filters(types, distinctRoomValues("floor", propertyId, companyId), beds,
                distinctRoomValues("housekeepingSection", propertyId, companyId), List.copyOf(features));
    }

    private List<String> distinctRoomValues(String field, Long propertyId, Long companyId) {
        // field is exclusively one of the constant internal call-site values above.
        return em.createQuery("select distinct r." + field + " from Room r where r.property.id = :propertyId and r.property.company.id = :companyId and r." + field + " is not null order by r." + field, String.class)
                .setParameter("propertyId", propertyId).setParameter("companyId", companyId).getResultList().stream().filter(this::hasText).toList();
    }

    private RoomView roomView(Room r) {
        RoomType t = r.getRoomType();
        return new RoomView(r.getId(), r.getNumber(), r.getName(), r.getFloor(), r.getHousekeepingSection(), t.getId(), t.getName(),
                t.getBedType(), t.getMaxOccupancy(), r.getFeatures(), r.getOperationalStatus(), r.getHousekeepingStatus(), r.isActive());
    }

    private ReservationView reservationView(Reservation r) {
        return reservationView(r, null);
    }

    private ReservationView reservationView(Reservation r, ReservationRoomSegment segment) {
        List<Integer> ages = !hasText(r.getChildAges()) ? List.of() : Arrays.stream(r.getChildAges().split(",")).map(String::trim).map(Integer::valueOf).toList();
        return new ReservationView(r.getId(), r.getVersion(), r.getConfirmationCode(), r.getGuest().getId(),
                r.getGuest().getFirstName() + " " + r.getGuest().getLastName(),
                segment == null ? r.getRoom().getId() : segment.getRoom().getId(),
                segment == null ? r.getRoomType().getId() : segment.getRoom().getRoomType().getId(),
                segment == null ? r.getRatePlan().getId() : segment.getRatePlan().getId(),
                r.getArrivalDate(), r.getDepartureDate(), r.getAdults(), r.getChildren(), ages, r.getStatus(), r.getSource(),
                r.getGuaranteeStatus(), r.getHoldUntil(), r.getNotes(), r.getGuestPreferenceSnapshot(),
                segment == null ? null : segment.getId(), segment == null ? r.getArrivalDate() : segment.getStartDate(),
                segment == null ? r.getDepartureDate() : segment.getEndDate());
    }

    private Expression<String> replace(CriteriaBuilder cb, Expression<String> value, String from, String to) {
        return cb.function("replace", String.class, value, cb.literal(from), cb.literal(to));
    }
    private boolean hasText(String s) { return s != null && !s.isBlank(); }
    private static String escapeLike(String value) { return value.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
    private void validate(PmsRoomPlanFilter f) {
        if (f.days() < 1 || f.days() > 90 || f.size() < 1 || f.size() > 100 || f.page() < 0 || f.page() > 1_000_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zeitraum: 1–90 Tage; Seitengröße: 1–100 Zimmer; gültige Seite erforderlich.");
        if (f.guests() != null && (f.guests() < 1 || f.guests() > 100))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personenzahl muss zwischen 1 und 100 liegen.");
        if (f.search() != null && f.search().length() > 200 || f.features() != null && (f.features().size() > 30
                || f.features().stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 120 || s.matches(".*[,;\\r\\n].*"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suchtext oder Ausstattungsfilter ist zu lang oder ungültig.");
    }
}
