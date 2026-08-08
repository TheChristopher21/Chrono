package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.ExternalBookingReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExternalBookingReferenceRepository extends JpaRepository<ExternalBookingReference, Long> {
    Optional<ExternalBookingReference> findByProperty_IdAndChannelCodeIgnoreCaseAndExternalId(
            Long propertyId,
            String channelCode,
            String externalId
    );
}
