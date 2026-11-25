package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.PayrollConfigurationVersion;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollConfigurationRepository extends JpaRepository<PayrollConfigurationVersion, Long> {
    List<PayrollConfigurationVersion> findByCompanyIdOrderByEffectiveFromDescIdDesc(Long companyId);
    Optional<PayrollConfigurationVersion> findFirstByCompanyIdAndUserIdIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(Long companyId, LocalDate date);
    Optional<PayrollConfigurationVersion> findFirstByCompanyIdAndUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(Long companyId, Long userId, LocalDate date);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select v from PayrollConfigurationVersion v where v.companyId=:companyId and v.userId is null and v.effectiveFrom<=:date order by v.effectiveFrom desc,v.id desc")
    List<PayrollConfigurationVersion> currentInsurance(Long companyId,LocalDate date);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select v from PayrollConfigurationVersion v where v.companyId=:companyId and v.userId=:userId and v.effectiveFrom<=:date order by v.effectiveFrom desc,v.id desc")
    List<PayrollConfigurationVersion> currentPension(Long companyId,Long userId,LocalDate date);
}

