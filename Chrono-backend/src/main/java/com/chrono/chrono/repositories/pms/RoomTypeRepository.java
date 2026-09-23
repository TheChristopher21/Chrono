package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    List<RoomType> findAllByProperty_IdOrderBySortOrderAscNameAsc(Long propertyId);
    Optional<RoomType> findByIdAndProperty_Company_Id(Long id, Long companyId);
    boolean existsByProperty_IdAndCodeIgnoreCase(Long propertyId, String code);
    boolean existsByProperty_IdAndCodeIgnoreCaseAndIdNot(Long propertyId, String code, Long id);
}
