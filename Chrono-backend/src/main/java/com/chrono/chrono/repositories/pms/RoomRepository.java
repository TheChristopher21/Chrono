package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.Room;
import com.chrono.chrono.entities.pms.RoomOperationalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findAllByProperty_IdOrderByFloorAscNumberAsc(Long propertyId);
    Optional<Room> findByIdAndProperty_Company_Id(Long id, Long companyId);
    long countByProperty_IdAndRoomType_IdAndActiveTrueAndOperationalStatus(
            Long propertyId,
            Long roomTypeId,
            RoomOperationalStatus operationalStatus
    );
    boolean existsByProperty_IdAndNumberIgnoreCase(Long propertyId, String number);
    boolean existsByProperty_IdAndNumberIgnoreCaseAndIdNot(Long propertyId, String number, Long id);
}
