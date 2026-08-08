package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.ResourceBooking;
import com.chrono.chrono.entities.pms.ResourceBookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResourceBookingRepository extends JpaRepository<ResourceBooking, Long> {
    List<ResourceBooking> findAllByProperty_IdOrderByStartAtDesc(Long propertyId);
    Optional<ResourceBooking> findByIdAndProperty_Company_Id(Long id, Long companyId);

    @Query("""
            select count(b) from ResourceBooking b
            where b.resource.id = :resourceId
              and b.status not in :excludedStatuses
              and b.startAt < :endAt
              and b.endAt > :startAt
            """)
    long countOverlapping(
            @Param("resourceId") Long resourceId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("excludedStatuses") Collection<ResourceBookingStatus> excludedStatuses
    );
}
