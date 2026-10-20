package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.PurchaseOrder;
import com.chrono.chrono.entities.inventory.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);
    Optional<PurchaseOrder> findByOrderNumberIgnoreCase(String orderNumber);
    Optional<PurchaseOrder> findByIdAndCompany_Id(Long id, Long companyId);
    Optional<PurchaseOrder> findByOrderNumberIgnoreCaseAndCompany_Id(String orderNumber, Long companyId);
    List<PurchaseOrder> findAllByCompany_Id(Long companyId);
    Page<PurchaseOrder> findAllByCompany_Id(Long companyId, Pageable pageable);
}
