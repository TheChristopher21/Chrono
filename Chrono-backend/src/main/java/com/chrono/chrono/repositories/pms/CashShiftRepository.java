package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.CashShift;
import com.chrono.chrono.entities.pms.CashShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {
    Optional<CashShift> findFirstByProperty_IdAndStatusOrderByOpenedAtDesc(Long propertyId, CashShiftStatus status);
    List<CashShift> findAllByProperty_IdOrderByOpenedAtDesc(Long propertyId);
    List<CashShift> findAllByProperty_IdAndStatusOrderByOpenedAtDesc(Long propertyId, CashShiftStatus status);
    boolean existsByProperty_IdAndRegisterCodeAndStatus(Long propertyId, String registerCode, CashShiftStatus status);
    Optional<CashShift> findByIdAndProperty_Id(Long id, Long propertyId);
}
