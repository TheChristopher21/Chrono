package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.GroupBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupBookingRepository extends JpaRepository<GroupBooking, Long> {
    List<GroupBooking> findAllByProperty_IdOrderByArrivalDateDesc(Long propertyId);
    Optional<GroupBooking> findByIdAndProperty_Company_Id(Long id, Long companyId);
    boolean existsByProperty_IdAndGroupCodeIgnoreCase(Long propertyId, String groupCode);
}
