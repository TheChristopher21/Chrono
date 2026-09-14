package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.RateOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RateOverrideRepository extends JpaRepository<RateOverride, Long> {
    List<RateOverride> findAllByRatePlan_Property_IdAndStayDateBetweenOrderByStayDateAsc(
            Long propertyId,
            LocalDate from,
            LocalDate to
    );
    List<RateOverride> findAllByRatePlan_IdAndStayDateBetweenOrderByStayDateAsc(
            Long ratePlanId,
            LocalDate from,
            LocalDate to
    );
    Optional<RateOverride> findByRatePlan_IdAndStayDate(Long ratePlanId, LocalDate stayDate);
}
