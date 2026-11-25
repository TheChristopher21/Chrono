package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.AnalyticsExcludedIp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalyticsExcludedIpRepository extends JpaRepository<AnalyticsExcludedIp, Long> {
    boolean existsByIpAddress(String ipAddress);

    Optional<AnalyticsExcludedIp> findByIpAddress(String ipAddress);

    List<AnalyticsExcludedIp> findAllByOrderByCreatedAtAsc();
}
