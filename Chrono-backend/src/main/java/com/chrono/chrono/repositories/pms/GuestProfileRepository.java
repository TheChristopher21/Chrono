package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.GuestProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuestProfileRepository extends JpaRepository<GuestProfile, Long> {
    List<GuestProfile> findAllByCompany_IdOrderByLastNameAscFirstNameAsc(Long companyId);
    Optional<GuestProfile> findByIdAndCompany_Id(Long id, Long companyId);
}
