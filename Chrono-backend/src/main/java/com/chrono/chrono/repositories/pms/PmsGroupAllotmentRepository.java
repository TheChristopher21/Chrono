package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsGroupAllotment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PmsGroupAllotmentRepository extends JpaRepository<PmsGroupAllotment,Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="roomType")
    List<PmsGroupAllotment> findAllByGroupBooking_IdOrderByStartDateAscIdAsc(Long groupId);
    List<PmsGroupAllotment> findAllByGroupBooking_Property_IdAndRoomType_IdAndReleasedFalse(Long propertyId,Long roomTypeId);
}
