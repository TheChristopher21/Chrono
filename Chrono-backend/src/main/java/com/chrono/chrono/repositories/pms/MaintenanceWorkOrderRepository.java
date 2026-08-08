package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.MaintenanceWorkOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaintenanceWorkOrderRepository extends JpaRepository<MaintenanceWorkOrder, Long> {
    List<MaintenanceWorkOrder> findAllByProperty_IdOrderByReportedAtDesc(Long propertyId);
    List<MaintenanceWorkOrder> findAllByProperty_IdOrderByReportedAtDesc(Long propertyId, Pageable pageable);
    Optional<MaintenanceWorkOrder> findByIdAndProperty_Company_Id(Long id, Long companyId);
}
