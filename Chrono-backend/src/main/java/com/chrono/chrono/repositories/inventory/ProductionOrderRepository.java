package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.ProductionOrder;
import com.chrono.chrono.entities.inventory.ProductionOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {
    List<ProductionOrder> findByStatus(ProductionOrderStatus status);
}
