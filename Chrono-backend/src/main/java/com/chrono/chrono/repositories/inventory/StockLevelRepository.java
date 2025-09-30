package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.Product;
import com.chrono.chrono.entities.inventory.StockLevel;
import com.chrono.chrono.entities.inventory.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {
    Optional<StockLevel> findByProductAndWarehouse(Product product, Warehouse warehouse);
}
