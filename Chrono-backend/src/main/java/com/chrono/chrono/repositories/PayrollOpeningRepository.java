package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.PayrollOpeningVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.List;

public interface PayrollOpeningRepository extends JpaRepository<PayrollOpeningVersion, Long> {
    List<PayrollOpeningVersion> findByCompanyIdOrderByIdDesc(Long companyId);
    List<PayrollOpeningVersion> findByCorrectionBatch(String correctionBatch);
    @Query("select o from PayrollOpeningVersion o where o.companyId=:companyId and o.userId=:userId and o.payrollStart between :yearStart and :yearEnd order by o.id desc")
    List<PayrollOpeningVersion> year(Long companyId, Long userId, LocalDate yearStart, LocalDate yearEnd);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PayrollOpeningVersion o where o.companyId=:companyId and o.userId=:userId and o.payrollStart between :yearStart and :yearEnd order by o.id desc")
    List<PayrollOpeningVersion> yearForUpdate(Long companyId, Long userId, LocalDate yearStart, LocalDate yearEnd);
}
