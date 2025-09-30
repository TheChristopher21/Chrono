package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.PurchaseOrder;
import com.chrono.chrono.entities.inventory.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);
}
