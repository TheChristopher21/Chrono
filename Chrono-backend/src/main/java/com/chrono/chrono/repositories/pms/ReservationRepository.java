package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.Reservation;
import com.chrono.chrono.entities.pms.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Query("select r.id from Reservation r where r.property.id=:propertyId and r.id>:cursor and r.status=com.chrono.chrono.entities.pms.ReservationStatus.CONFIRMED and r.policyDepositPercent>0 order by r.id")
    List<Long> findDepositCandidates(@Param("propertyId") Long propertyId,@Param("cursor") Long cursor,org.springframework.data.domain.Pageable page);
    Optional<Reservation> findByIdAndProperty_Company_Id(Long id, Long companyId);
    @Query("select r.property.id from Reservation r where r.id=:id and r.property.company.id=:companyId")
    Optional<Long> findPropertyIdForTenant(@Param("id") Long id,@Param("companyId") Long companyId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {
            "property", "guest", "roomType", "ratePlan", "room", "groupBooking", "roomSegments", "roomSegments.room"
    })
    List<Reservation> findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
            Long propertyId,
            LocalDate toExclusive,
            LocalDate fromExclusive
    );

    @Query("""
            select count(r) from Reservation r
            where r.property.id = :propertyId
              and ((not exists (select s.id from ReservationRoomSegment s where s.reservation = r) and r.roomType.id = :roomTypeId)
                   or exists (select s.id from ReservationRoomSegment s where s.reservation = r and s.room.roomType.id = :roomTypeId and s.startDate < :departure and s.endDate > :arrival))
              and r.status not in :excludedStatuses
              and r.arrivalDate < :departure
              and r.departureDate > :arrival
              and (:excludeId is null or r.id <> :excludeId)
            """)
    long countOverlappingByRoomType(
            @Param("propertyId") Long propertyId,
            @Param("roomTypeId") Long roomTypeId,
            @Param("arrival") LocalDate arrival,
            @Param("departure") LocalDate departure,
            @Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            select count(r) from Reservation r
            where ((not exists (select s.id from ReservationRoomSegment s where s.reservation = r) and r.room.id = :roomId)
                   or exists (select s.id from ReservationRoomSegment s where s.reservation = r and s.room.id = :roomId and s.startDate < :departure and s.endDate > :arrival))
              and r.status not in :excludedStatuses
              and r.arrivalDate < :departure
              and r.departureDate > :arrival
              and (:excludeId is null or r.id <> :excludeId)
            """)
    long countOverlappingByRoom(
            @Param("roomId") Long roomId,
            @Param("arrival") LocalDate arrival,
            @Param("departure") LocalDate departure,
            @Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses,
            @Param("excludeId") Long excludeId
    );

    long countByProperty_IdAndStatus(Long propertyId, ReservationStatus status);
    List<Reservation> findAllByGroupBooking_IdOrderByGuest_LastNameAsc(Long groupBookingId);
    List<Reservation> findAllByGuest_IdOrderByArrivalDateDesc(Long guestId);
    long countByProperty_IdAndArrivalDateAndStatusIn(
            Long propertyId,
            LocalDate arrivalDate,
            Collection<ReservationStatus> statuses
    );
    long countByProperty_IdAndDepartureDateAndStatusIn(
            Long propertyId,
            LocalDate departureDate,
            Collection<ReservationStatus> statuses
    );

    List<Reservation> findAllByStatusInAndHoldUntilBefore(
            Collection<ReservationStatus> statuses,
            LocalDateTime holdUntil
    );
}
