package com.chrono.chrono.repositories.banking;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.banking.DigitalSignatureRequest;
import com.chrono.chrono.entities.banking.SignatureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DigitalSignatureRequestRepository extends JpaRepository<DigitalSignatureRequest, Long> {
    List<DigitalSignatureRequest> findByStatus(SignatureStatus status);
    List<DigitalSignatureRequest> findByCompanyOrderByRequestedAtDesc(Company company);
    Optional<DigitalSignatureRequest> findByIdAndCompany(Long id, Company company);
}
