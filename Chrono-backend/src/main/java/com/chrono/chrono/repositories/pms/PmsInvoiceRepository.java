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
}
