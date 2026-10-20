package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.PayrollConfigurationVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollConfigurationRepository extends JpaRepository<PayrollConfigurationVersion, Long> {
    List<PayrollConfigurationVersion> findByCompanyIdOrderByEffectiveFromDescIdDesc(Long companyId);
    Optional<PayrollConfigurationVersion> findFirstByCompanyIdAndUserIdIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(Long companyId, LocalDate date);
    Optional<PayrollConfigurationVersion> findFirstByCompanyIdAndUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(Long companyId, Long userId, LocalDate date);
}

