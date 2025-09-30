package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.Campaign;
import com.chrono.chrono.entities.crm.CampaignStatus;

import java.time.LocalDate;

public class CampaignDTO {
    private final Long id;
    private final String name;
    private final CampaignStatus status;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public CampaignDTO(Long id, String name, CampaignStatus status, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static CampaignDTO from(Campaign campaign) {
        return new CampaignDTO(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getStartDate(),
                campaign.getEndDate());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
