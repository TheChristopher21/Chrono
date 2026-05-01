package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.ProductionOrder;
import com.chrono.chrono.entities.inventory.ProductionOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {
    List<ProductionOrder> findByStatus(ProductionOrderStatus status);
    Optional<ProductionOrder> findByOrderNumberIgnoreCase(String orderNumber);
    Optional<ProductionOrder> findByIdAndCompany_Id(Long id, Long companyId);
    Optional<ProductionOrder> findByOrderNumberIgnoreCaseAndCompany_Id(String orderNumber, Long companyId);
    Page<ProductionOrder> findAllByCompany_Id(Long companyId, Pageable pageable);
}
