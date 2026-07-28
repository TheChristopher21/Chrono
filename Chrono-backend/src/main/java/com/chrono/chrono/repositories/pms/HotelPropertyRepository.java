package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.HotelProperty;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HotelPropertyRepository extends JpaRepository<HotelProperty, Long> {
    List<HotelProperty> findAllByCompany_IdOrderByNameAsc(Long companyId);
    Optional<HotelProperty> findByIdAndCompany_Id(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from HotelProperty p where p.id = :id and p.company.id = :companyId")
    Optional<HotelProperty> findByIdAndCompany_IdForUpdate(@Param("id") Long id,
                                                           @Param("companyId") Long companyId);

    boolean existsByCompany_IdAndCodeIgnoreCase(Long companyId, String code);
    boolean existsByCompany_IdAndCodeIgnoreCaseAndIdNot(Long companyId, String code, Long id);
}
