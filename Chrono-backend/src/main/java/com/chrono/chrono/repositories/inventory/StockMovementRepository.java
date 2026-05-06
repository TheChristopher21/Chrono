package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findAllByProduct_Company_Id(Long companyId);

    Page<StockMovement> findAllByProduct_Company_Id(Long companyId, Pageable pageable);
}
