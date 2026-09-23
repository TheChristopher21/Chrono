package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PublicBookingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicBookingRequestRepository extends JpaRepository<PublicBookingRequest, Long> {
    Optional<PublicBookingRequest> findByProperty_IdAndIdempotencyKey(Long propertyId, String idempotencyKey);
}
