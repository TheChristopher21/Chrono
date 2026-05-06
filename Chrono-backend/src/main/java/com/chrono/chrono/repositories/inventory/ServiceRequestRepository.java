package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.ServiceRequest;
import com.chrono.chrono.entities.inventory.ServiceRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByStatus(ServiceRequestStatus status);
    Optional<ServiceRequest> findByIdAndCompany_Id(Long id, Long companyId);
    List<ServiceRequest> findAllByCompany_Id(Long companyId);
    Page<ServiceRequest> findAllByCompany_Id(Long companyId, Pageable pageable);
}
