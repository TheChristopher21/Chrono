package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmsInvoiceLineRepository extends JpaRepository<PmsInvoiceLine, Long> {
    List<PmsInvoiceLine> findAllByInvoice_IdOrderByIdAsc(Long invoiceId);
    boolean existsByActiveSourceItemId(Long itemId);
    boolean existsBySourceItem_Id(Long itemId);

    @org.springframework.data.jpa.repository.Query("select l.activeSourceItemId from PmsInvoiceLine l where l.activeSourceItemId in :itemIds")
    List<Long> findAllocatedSourceIds(@org.springframework.data.repository.query.Param("itemIds") List<Long> itemIds);

    @org.springframework.data.jpa.repository.Query("select l.activeSourceItemId from PmsInvoiceLine l where l.sourceItem.folio.id in :folioIds and l.activeSourceItemId is not null")
    List<Long> findAllocatedSourceIdsByFolioIds(@org.springframework.data.repository.query.Param("folioIds") List<Long> folioIds);
}
