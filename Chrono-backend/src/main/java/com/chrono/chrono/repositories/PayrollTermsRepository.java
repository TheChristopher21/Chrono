package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.PayrollTermsVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollTermsRepository extends JpaRepository<PayrollTermsVersion, Long> {
    List<PayrollTermsVersion> findByCompanyIdOrderByEffectiveFromDescIdDesc(Long companyId);
    Optional<PayrollTermsVersion> findFirstByCompanyIdAndUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(Long companyId, Long userId, LocalDate date);
}
