package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "enabledFeatures")
    java.util.Optional<Company> findWithFeaturesById(Long id);
    // optional: Company findByName(String name);
    List<Company> findByDemoTrueAndDemoExpiresAtBefore(LocalDateTime cutoff);
}
