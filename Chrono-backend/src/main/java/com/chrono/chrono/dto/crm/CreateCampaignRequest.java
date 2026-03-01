package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.Campaign;
import com.chrono.chrono.entities.crm.CampaignStatus;

import java.time.LocalDate;

public class CreateCampaignRequest {
    private String name;
    private CampaignStatus status = CampaignStatus.PLANNED;
    private LocalDate startDate;
    private LocalDate endDate;
    private String channel;
    private Integer budget;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Integer getBudget() {
        return budget;
    }

    public void setBudget(Integer budget) {
        this.budget = budget;
    }

    public Campaign toEntity() {
        Campaign campaign = new Campaign();
        campaign.setName(name);
        campaign.setStatus(status);
        campaign.setStartDate(startDate);
        campaign.setEndDate(endDate);
        campaign.setChannel(channel);
        campaign.setBudget(budget);
        return campaign;
    }
}
