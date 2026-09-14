package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.RoomBlock;
import com.chrono.chrono.entities.pms.RoomBlockStatus;
import com.chrono.chrono.entities.pms.RoomBlockType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RoomBlockRepository extends JpaRepository<RoomBlock, Long> {
    List<RoomBlock> findAllByProperty_IdAndStartDateLessThanAndEndDateGreaterThanOrderByStartDateAsc(
            Long propertyId, LocalDate toExclusive, LocalDate fromExclusive);

    @Query("""
            select count(distinct b.room.id) from RoomBlock b
            where b.property.id = :propertyId
              and b.room.roomType.id = :roomTypeId
              and b.status = :status
              and b.type in :blockingTypes
              and b.startDate < :departure
              and b.endDate > :arrival
            """)
    long countInventoryBlockingRooms(
            @Param("propertyId") Long propertyId,
            @Param("roomTypeId") Long roomTypeId,
            @Param("arrival") LocalDate arrival,
            @Param("departure") LocalDate departure,
            @Param("status") RoomBlockStatus status,
            @Param("blockingTypes") Collection<RoomBlockType> blockingTypes
    );

    @Query("""
            select count(b) from RoomBlock b
            where b.room.id = :roomId
              and b.status = :status
              and b.startDate < :departure
              and b.endDate > :arrival
            """)
    long countRoomBlocks(
            @Param("roomId") Long roomId,
            @Param("arrival") LocalDate arrival,
            @Param("departure") LocalDate departure,
            @Param("status") RoomBlockStatus status
    );

    @Query("""
            select count(b) from RoomBlock b
            where b.room.id = :roomId
              and b.status = :status
              and b.type in :blockingTypes
              and b.startDate < :departure
              and b.endDate > :arrival
            """)
    long countInventoryBlockingRoomBlocks(
            @Param("roomId") Long roomId,
            @Param("arrival") LocalDate arrival,
            @Param("departure") LocalDate departure,
            @Param("status") RoomBlockStatus status,
            @Param("blockingTypes") Collection<RoomBlockType> blockingTypes
    );
}
