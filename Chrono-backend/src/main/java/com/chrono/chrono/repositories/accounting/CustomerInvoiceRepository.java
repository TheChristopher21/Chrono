package com.chrono.chrono.repositories.accounting;

import com.chrono.chrono.entities.accounting.CustomerInvoice;
import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CustomerInvoiceRepository extends JpaRepository<CustomerInvoice, Long> {
    Page<CustomerInvoice> findByStatusIn(Collection<InvoiceStatus> statuses, Pageable pageable);
    Page<CustomerInvoice> findByCompanyAndStatusIn(Company company, Collection<InvoiceStatus> statuses, Pageable pageable);
    Optional<CustomerInvoice> findByIdAndCompany(Long id, Company company);
}
