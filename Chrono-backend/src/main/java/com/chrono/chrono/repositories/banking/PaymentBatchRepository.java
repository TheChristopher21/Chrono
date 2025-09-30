package com.chrono.chrono.repositories.banking;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.banking.PaymentBatch;
import com.chrono.chrono.entities.banking.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, Long> {
    List<PaymentBatch> findByCompanyAndStatusIn(Company company, List<PaymentStatus> statuses);
    Page<PaymentBatch> findByCompanyAndStatusIn(Company company, List<PaymentStatus> statuses, Pageable pageable);
}
