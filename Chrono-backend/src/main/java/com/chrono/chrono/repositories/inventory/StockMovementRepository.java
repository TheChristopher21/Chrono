package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
}
