package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.FolioItem;
import com.chrono.chrono.entities.pms.FolioItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolioItemRepository extends JpaRepository<FolioItem, Long> {
    List<FolioItem> findAllByFolio_IdOrderByServiceDateAscIdAsc(Long folioId);
    void deleteAllByFolio_IdAndType(Long folioId, FolioItemType type);
}
