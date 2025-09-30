package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.Opportunity;
import com.chrono.chrono.entities.crm.OpportunityStage;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateOpportunityRequest {
    private String title;
    private OpportunityStage stage = OpportunityStage.QUALIFICATION;
    private BigDecimal value;
    private Double probability;
    private LocalDate expectedCloseDate;
    private Long customerId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OpportunityStage getStage() {
        return stage;
    }

    public void setStage(OpportunityStage stage) {
        this.stage = stage;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public Double getProbability() {
        return probability;
    }

    public void setProbability(Double probability) {
        this.probability = probability;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public void setExpectedCloseDate(LocalDate expectedCloseDate) {
        this.expectedCloseDate = expectedCloseDate;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Opportunity toEntity() {
        Opportunity opportunity = new Opportunity();
        opportunity.setTitle(title);
        opportunity.setStage(stage);
        opportunity.setValue(value);
        opportunity.setProbability(probability);
        opportunity.setExpectedCloseDate(expectedCloseDate);
        return opportunity;
    }
}
