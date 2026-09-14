package com.chrono.chrono.repositories.inventory;

import com.chrono.chrono.entities.inventory.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    Optional<Product> findBySkuIgnoreCase(String sku);
    Optional<Product> findByIdAndCompany_Id(Long id, Long companyId);
    Optional<Product> findBySkuIgnoreCaseAndCompany_Id(String sku, Long companyId);
    List<Product> findAllByCompany_Id(Long companyId);
    Page<Product> findAllByCompany_Id(Long companyId, Pageable pageable);
}
