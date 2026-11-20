package com.chrono.chrono.repositories.accounting;

import com.chrono.chrono.entities.accounting.Asset;
import com.chrono.chrono.entities.accounting.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByStatus(AssetStatus status);
    List<Asset> findByCompany_IdOrderByIdAsc(Long companyId);
    java.util.Optional<Asset> findByIdAndCompany_Id(Long id, Long companyId);
    List<Asset> findByCompanyIsNullOrderByIdAsc();
    boolean existsByCompanyIsNull();
}
