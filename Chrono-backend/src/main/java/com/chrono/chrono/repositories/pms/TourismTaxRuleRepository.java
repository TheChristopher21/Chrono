package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.TourismTaxRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TourismTaxRuleRepository extends JpaRepository<TourismTaxRule, Long> {
    Optional<TourismTaxRule> findByProperty_Id(Long propertyId);
}
