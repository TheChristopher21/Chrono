package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByCode(String code);
    Optional<Warehouse> findByIdAndCompany_Id(Long id, Long companyId);
    Optional<Warehouse> findByCodeAndCompany_Id(String code, Long companyId);
    List<Warehouse> findAllByCompany_Id(Long companyId);
    Page<Warehouse> findAllByCompany_Id(Long companyId, Pageable pageable);
}
