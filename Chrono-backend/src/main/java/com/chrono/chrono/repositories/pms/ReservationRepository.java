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
    Optional<Reservation> findByIdAndProperty_Company_Id(Long id, Long companyId);

    List<Reservation> findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(
            Long propertyId,
            LocalDate toExclusive,
            LocalDate fromExclusive
    );

    @Query("""
            select count(r) from Reservation r
            where r.property.id = :propertyId
              and r.roomType.id = :roomTypeId
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
            where r.room.id = :roomId
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
