package com.chrono.chrono.repositories.accounting;

import com.chrono.chrono.entities.accounting.Asset;
import com.chrono.chrono.entities.accounting.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByStatus(AssetStatus status);
}
