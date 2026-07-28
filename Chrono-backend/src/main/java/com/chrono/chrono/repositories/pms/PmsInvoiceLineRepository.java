package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PmsInvoiceLineRepository extends JpaRepository<PmsInvoiceLine, Long> {
    List<PmsInvoiceLine> findAllByInvoice_IdOrderByIdAsc(Long invoiceId);
}
