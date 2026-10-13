package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.Room;
import com.chrono.chrono.entities.pms.RoomOperationalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    interface RoomTypeCount { Long getRoomTypeId(); long getRoomCount(); }

    @org.springframework.data.jpa.repository.Query("select r.roomType.id as roomTypeId, count(r) as roomCount from Room r where r.property.id = :propertyId group by r.roomType.id")
    List<RoomTypeCount> countRoomsByType(@org.springframework.data.repository.query.Param("propertyId") Long propertyId);
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
