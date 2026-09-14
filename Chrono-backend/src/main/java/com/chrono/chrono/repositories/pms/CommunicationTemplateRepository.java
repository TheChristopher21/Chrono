package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.CommunicationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunicationTemplateRepository extends JpaRepository<CommunicationTemplate, Long> {
    List<CommunicationTemplate> findAllByProperty_IdOrderByNameAsc(Long propertyId);
    Optional<CommunicationTemplate> findByIdAndProperty_Company_Id(Long id, Long companyId);
    boolean existsByProperty_IdAndCodeIgnoreCase(Long propertyId, String code);
}
