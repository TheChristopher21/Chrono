package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.FolioItem;
import com.chrono.chrono.entities.pms.FolioItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;

public interface FolioItemRepository extends JpaRepository<FolioItem, Long> {
    List<FolioItem> findAllByFolio_IdOrderByServiceDateAscIdAsc(Long folioId);
    List<FolioItem> findAllByFolio_IdInOrderByServiceDateAscIdAsc(List<Long> folioIds);
    void deleteAllByFolio_IdAndType(Long folioId, FolioItemType type);
    List<FolioItem> findAllByFolio_Reservation_Property_IdAndServiceDateGreaterThanEqualAndServiceDateLessThanOrderByServiceDateAscIdAsc(
            Long propertyId, LocalDate from, LocalDate toExclusive);
}
