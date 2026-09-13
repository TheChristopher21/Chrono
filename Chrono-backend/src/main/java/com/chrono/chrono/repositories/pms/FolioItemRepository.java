package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.FolioItem;
import com.chrono.chrono.entities.pms.FolioItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;

public interface FolioItemRepository extends JpaRepository<FolioItem, Long> {
    List<FolioItem> findAllByFolio_IdOrderByServiceDateAscIdAsc(Long folioId);
    @org.springframework.data.jpa.repository.Query("select i from FolioItem i where coalesce(i.sourceReservation.id,i.folio.reservation.id)=:reservationId and i.rateGenerated=true order by i.serviceDate,i.id")
    List<FolioItem> findAllByFolio_Reservation_IdAndRateGeneratedTrueOrderByServiceDateAscIdAsc(@org.springframework.data.repository.query.Param("reservationId") Long reservationId);
    @org.springframework.data.jpa.repository.Query("select i from FolioItem i where coalesce(i.sourceReservation.id,i.folio.reservation.id)=:reservationId order by i.serviceDate,i.id")
    List<FolioItem> findAllBySourceReservation(@org.springframework.data.repository.query.Param("reservationId") Long reservationId);
    List<FolioItem> findAllByFolio_IdInOrderByServiceDateAscIdAsc(List<Long> folioIds);
    void deleteAllByFolio_IdAndType(Long folioId, FolioItemType type);
    void deleteAllByFolio_IdAndRateGeneratedTrue(Long folioId);
    List<FolioItem> findAllByFolio_Reservation_Property_IdAndServiceDateGreaterThanEqualAndServiceDateLessThanOrderByServiceDateAscIdAsc(
            Long propertyId, LocalDate from, LocalDate toExclusive);
}
