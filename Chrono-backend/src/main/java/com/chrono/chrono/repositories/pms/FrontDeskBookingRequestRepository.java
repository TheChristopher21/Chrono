package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.FrontDeskBookingRequestRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FrontDeskBookingRequestRepository extends JpaRepository<FrontDeskBookingRequestRecord, Long> {
    Optional<FrontDeskBookingRequestRecord> findByProperty_IdAndIdempotencyKey(
            Long propertyId,
            String idempotencyKey
    );
}
