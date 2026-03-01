package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.IntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, Long> {
    List<IntegrationConfig> findByCompanyId(Long companyId);
}
