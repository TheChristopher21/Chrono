package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.RatePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RatePlanRepository extends JpaRepository<RatePlan, Long> {
    List<RatePlan> findAllByProperty_IdOrderByRoomType_SortOrderAscNameAsc(Long propertyId);
    Optional<RatePlan> findByIdAndProperty_Company_Id(Long id, Long companyId);
    boolean existsByProperty_IdAndCodeIgnoreCase(Long propertyId, String code);
    boolean existsByProperty_IdAndCodeIgnoreCaseAndIdNot(Long propertyId, String code, Long id);
}
