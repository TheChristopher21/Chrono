package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.HotelResource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HotelResourceRepository extends JpaRepository<HotelResource, Long> {
    List<HotelResource> findAllByProperty_IdOrderByTypeAscNameAsc(Long propertyId);
    Optional<HotelResource> findByIdAndProperty_Company_Id(Long id, Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from HotelResource r where r.id = :id and r.property.company.id = :companyId")
    Optional<HotelResource> findByIdAndProperty_Company_IdForUpdate(
            @Param("id") Long id,
            @Param("companyId") Long companyId);
    boolean existsByProperty_IdAndCodeIgnoreCase(Long propertyId, String code);
}
