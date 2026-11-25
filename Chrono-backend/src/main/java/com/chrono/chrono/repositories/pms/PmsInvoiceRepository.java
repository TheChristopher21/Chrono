package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PmsInvoiceRepository extends JpaRepository<PmsInvoice, Long> {
    List<PmsInvoice> findAllByProperty_IdOrderByIssueDateDescIdDesc(Long propertyId);
    Optional<PmsInvoice> findByIdAndProperty_Company_Id(Long id, Long companyId);
    long countByProperty_Id(Long propertyId);
    List<PmsInvoice> findAllByFolio_IdOrderByIssueDateDesc(Long folioId);

    @org.springframework.data.jpa.repository.Query("""
            select count(i) from PmsInvoice i where i.folio.id = :folioId
            and i.type = com.chrono.chrono.entities.pms.InvoiceType.INVOICE
            and (i.status in (com.chrono.chrono.entities.pms.InvoiceStatus.ISSUED, com.chrono.chrono.entities.pms.InvoiceStatus.PAID)
                 or (i.status = com.chrono.chrono.entities.pms.InvoiceStatus.CREDITED and i.correctionMode = 'CANCEL_SERVICES'))
            and exists (select l.id from PmsInvoiceLine l where l.invoice = i and l.sourceItem is null)
            """)
    long countUnlinkedIssuedInvoices(@org.springframework.data.repository.query.Param("folioId") Long folioId);
}
