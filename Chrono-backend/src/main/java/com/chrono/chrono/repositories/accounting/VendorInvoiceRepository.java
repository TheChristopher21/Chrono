package com.chrono.chrono.repositories.accounting;

import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.entities.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface VendorInvoiceRepository extends JpaRepository<VendorInvoice, Long> {
    Page<VendorInvoice> findByStatusIn(Collection<InvoiceStatus> statuses, Pageable pageable);
    Page<VendorInvoice> findByCompanyAndStatusIn(Company company, Collection<InvoiceStatus> statuses, Pageable pageable);
    Optional<VendorInvoice> findByIdAndCompany(Long id, Company company);
}
