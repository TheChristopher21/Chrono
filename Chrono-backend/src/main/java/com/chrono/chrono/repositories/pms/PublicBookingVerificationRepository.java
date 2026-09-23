package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PublicBookingVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicBookingVerificationRepository extends JpaRepository<PublicBookingVerification, Long> {
    Optional<PublicBookingVerification> findByTokenHash(String tokenHash);
    Optional<PublicBookingVerification> findByBookingRequest_Id(Long bookingRequestId);
}
