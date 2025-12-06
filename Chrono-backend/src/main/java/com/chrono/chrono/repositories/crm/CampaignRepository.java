package com.chrono.chrono.repositories.crm;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.crm.Campaign;
import com.chrono.chrono.entities.crm.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByCompany(Company company);
    List<Campaign> findByCompanyAndStatus(Company company, CampaignStatus status);
}
