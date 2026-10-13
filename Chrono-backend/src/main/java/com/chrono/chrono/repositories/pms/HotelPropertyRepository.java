package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.HotelProperty;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HotelPropertyRepository extends JpaRepository<HotelProperty, Long> {
    @EntityGraph(attributePaths = "company")
    @Query("select property from HotelProperty property")
    List<HotelProperty> findAllWithCompany();

    List<HotelProperty> findAllByCompany_IdOrderByNameAsc(Long companyId);
    List<HotelProperty> findAllByCompany_IdAndIdInOrderByNameAsc(Long companyId, java.util.Collection<Long> ids);
    Optional<HotelProperty> findByIdAndCompany_Id(Long id, Long companyId);
    Optional<HotelProperty> findByCompany_IdAndCodeIgnoreCase(Long companyId, String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from HotelProperty p where p.id = :id and p.company.id = :companyId")
    Optional<HotelProperty> findByIdAndCompany_IdForUpdate(@Param("id") Long id,
                                                           @Param("companyId") Long companyId);

    boolean existsByCompany_IdAndCodeIgnoreCase(Long companyId, String code);
    boolean existsByCompany_IdAndCodeIgnoreCaseAndIdNot(Long companyId, String code, Long id);

    @Query("""
            select (count(p)>0) from HotelProperty p where p.id=:propertyId and (
              exists(select r.id from RatePlan r where r.property=p)
              or exists(select r.id from Reservation r where r.property=p)
              or exists(select c.id from CashShift c where c.property=p)
              or exists(select t.id from PosTicket t where t.property=p)
              or exists(select r.id from HotelResource r where r.property=p)
              or exists(select t.id from TourismTaxRule t where t.property=p)
              or exists(select s.id from PmsRevenueSnapshot s where s.property=p)
              or exists(select b.id from PmsRevenueBudget b where b.property=p))
            """)
    boolean hasCurrencyDependentRecords(@Param("propertyId") Long propertyId);
}
