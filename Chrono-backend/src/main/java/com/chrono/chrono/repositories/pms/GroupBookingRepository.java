package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.GroupBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupBookingRepository extends JpaRepository<GroupBooking, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"contactGuest","organization"})
    @org.springframework.data.jpa.repository.Query("select g from GroupBooking g where g.property.id=:propertyId and (:query='' or lower(g.name) like :query escape '!' or lower(g.groupCode) like :query escape '!') order by g.arrivalDate desc,g.id desc")
    org.springframework.data.domain.Page<GroupBooking> searchDirectory(@org.springframework.data.repository.query.Param("propertyId") Long propertyId,@org.springframework.data.repository.query.Param("query") String query,org.springframework.data.domain.Pageable pageable);
    List<GroupBooking> findAllByProperty_IdOrderByArrivalDateDesc(Long propertyId);
    List<GroupBooking> findAllByContactGuest_Id(Long guestId);
    List<GroupBooking> findAllByOrganization_Id(Long organizationId);
    Optional<GroupBooking> findByIdAndProperty_Company_Id(Long id, Long companyId);
    boolean existsByProperty_IdAndGroupCodeIgnoreCase(Long propertyId, String groupCode);
}
