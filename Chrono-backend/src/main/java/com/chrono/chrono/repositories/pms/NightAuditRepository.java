package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.NightAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NightAuditRepository extends JpaRepository<NightAudit, Long> {
    List<NightAudit> findAllByProperty_IdOrderByBusinessDateDesc(Long propertyId);
    Optional<NightAudit> findByProperty_IdAndBusinessDate(Long propertyId, LocalDate businessDate);
}
