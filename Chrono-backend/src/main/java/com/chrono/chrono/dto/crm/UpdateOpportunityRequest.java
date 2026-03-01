package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.OpportunityStage;

import java.time.LocalDate;

public class UpdateOpportunityRequest {
    private OpportunityStage stage;
    private Double probability;
    private LocalDate expectedCloseDate;

    public OpportunityStage getStage() {
        return stage;
    }

    public void setStage(OpportunityStage stage) {
        this.stage = stage;
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
}
