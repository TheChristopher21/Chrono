package com.chrono.chrono.repositories.accounting;

import com.chrono.chrono.entities.accounting.CustomerInvoice;
import com.chrono.chrono.entities.accounting.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInvoiceRepository extends JpaRepository<CustomerInvoice, Long> {
    Page<CustomerInvoice> findByStatus(InvoiceStatus status, Pageable pageable);
}
