package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsOrganization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PmsOrganizationRepository extends JpaRepository<PmsOrganization, Long> {
    List<PmsOrganization> findAllByCompany_IdOrderByNameAsc(Long companyId);
    Optional<PmsOrganization> findByIdAndCompany_Id(Long id, Long companyId);
}
