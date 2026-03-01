package com.chrono.chrono.repositories.crm;

import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.crm.CrmDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrmDocumentRepository extends JpaRepository<CrmDocument, Long> {
    List<CrmDocument> findByCustomerOrderByUploadedAtDesc(Customer customer);
}
