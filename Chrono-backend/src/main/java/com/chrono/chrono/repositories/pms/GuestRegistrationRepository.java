package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.GuestRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuestRegistrationRepository extends JpaRepository<GuestRegistration, Long> {
    Optional<GuestRegistration> findByReservation_Id(Long reservationId);
    Optional<GuestRegistration> findByTokenHash(String tokenHash);
    List<GuestRegistration> findAllByReservation_Property_IdOrderByCompletedAtDesc(Long propertyId);
}
