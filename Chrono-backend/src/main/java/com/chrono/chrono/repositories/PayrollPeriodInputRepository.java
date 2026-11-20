package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.PayrollPeriodInputVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;
import java.util.List;

public interface PayrollPeriodInputRepository extends JpaRepository<PayrollPeriodInputVersion,Long> {
    List<PayrollPeriodInputVersion> findByCompanyIdOrderByIdDesc(Long companyId);
    @Query("select p from PayrollPeriodInputVersion p where p.companyId=:companyId and p.userId=:userId and p.periodStart=:start and p.periodEnd=:end order by p.id desc")
    List<PayrollPeriodInputVersion> period(Long companyId,Long userId,LocalDate start,LocalDate end);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayrollPeriodInputVersion p where p.companyId=:companyId and p.userId=:userId and p.periodStart=:start and p.periodEnd=:end order by p.id desc")
    List<PayrollPeriodInputVersion> periodForUpdate(Long companyId,Long userId,LocalDate start,LocalDate end);
    @Query("select p from PayrollPeriodInputVersion p where p.companyId=:companyId and p.userId=:userId and p.periodStart between :start and :end order by p.id desc")
    List<PayrollPeriodInputVersion> year(Long companyId,Long userId,LocalDate start,LocalDate end);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayrollPeriodInputVersion p where p.companyId=:companyId and p.userId=:userId and p.periodStart between :start and :end order by p.id desc")
    List<PayrollPeriodInputVersion> yearForUpdate(Long companyId,Long userId,LocalDate start,LocalDate end);
}
