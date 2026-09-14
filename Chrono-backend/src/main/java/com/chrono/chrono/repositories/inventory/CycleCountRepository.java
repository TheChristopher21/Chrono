package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.CycleCount;
import com.chrono.chrono.entities.inventory.CycleCountStatus;
import com.chrono.chrono.entities.inventory.Product;
import com.chrono.chrono.entities.inventory.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CycleCountRepository extends JpaRepository<CycleCount, Long> {
    Optional<CycleCount> findFirstByProductAndWarehouseAndStatusInOrderByCreatedAtDesc(Product product,
                                                                                        Warehouse warehouse,
                                                                                        Collection<CycleCountStatus> statuses);
    Optional<CycleCount> findByIdAndCompany_Id(Long id, Long companyId);
    List<CycleCount> findAllByCompany_Id(Long companyId);
    Page<CycleCount> findAllByCompany_Id(Long companyId, Pageable pageable);
    long countByCompany_Id(Long companyId);
}
