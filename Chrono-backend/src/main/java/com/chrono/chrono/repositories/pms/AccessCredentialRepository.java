package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.AccessCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessCredentialRepository extends JpaRepository<AccessCredential, Long> {
    List<AccessCredential> findAllByProperty_IdOrderByIssuedAtDesc(Long propertyId);
    Optional<AccessCredential> findByIdAndProperty_Company_Id(Long id, Long companyId);
}
