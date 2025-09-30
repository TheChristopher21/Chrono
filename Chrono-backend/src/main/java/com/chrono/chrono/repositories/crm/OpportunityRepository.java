package com.chrono.chrono.repositories.crm;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.crm.Opportunity;
import com.chrono.chrono.entities.crm.OpportunityStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {
    List<Opportunity> findByCompanyAndStage(Company company, OpportunityStage stage);
}
