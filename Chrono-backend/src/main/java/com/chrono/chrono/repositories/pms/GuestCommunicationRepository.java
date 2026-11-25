package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.GuestCommunication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuestCommunicationRepository extends JpaRepository<GuestCommunication, Long> {
    List<GuestCommunication> findAllByProperty_IdOrderByCreatedAtDesc(Long propertyId);
    Optional<GuestCommunication> findByIdAndProperty_Company_Id(Long id, Long companyId);
    List<GuestCommunication> findAllByGuest_IdOrderByCreatedAtDesc(Long guestId);
}
