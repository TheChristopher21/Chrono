package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.Folio;
import com.chrono.chrono.entities.pms.FolioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FolioRepository extends JpaRepository<Folio, Long> {
    Optional<Folio> findFirstByReservation_IdOrderByIdAsc(Long reservationId);
    List<Folio> findAllByReservation_IdOrderByIdAsc(Long reservationId);
    Optional<Folio> findByIdAndReservation_Property_Company_Id(Long id, Long companyId);
    List<Folio> findAllByReservation_Property_IdOrderByCreatedAtDesc(Long propertyId);

    @Query("""
            select folio from Folio folio
            where folio.reservation.property.id = :propertyId
              and (folio.status = :openStatus
                   or (folio.reservation.arrivalDate < :rangeEnd
                       and folio.reservation.departureDate > :rangeStart))
            order by folio.createdAt desc
            """)
    List<Folio> findOperationalFolios(@Param("propertyId") Long propertyId,
                                      @Param("rangeStart") LocalDate rangeStart,
                                      @Param("rangeEnd") LocalDate rangeEnd,
                                      @Param("openStatus") FolioStatus openStatus);
}
