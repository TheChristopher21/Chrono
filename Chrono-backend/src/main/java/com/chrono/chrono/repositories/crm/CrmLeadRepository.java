package com.chrono.chrono.repositories.crm;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.crm.CrmLead;
import com.chrono.chrono.entities.crm.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrmLeadRepository extends JpaRepository<CrmLead, Long> {
    List<CrmLead> findByCompany(Company company);
    List<CrmLead> findByCompanyAndStatus(Company company, LeadStatus status);
}
