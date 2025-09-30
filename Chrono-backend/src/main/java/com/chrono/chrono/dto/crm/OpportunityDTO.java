package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.Opportunity;
import com.chrono.chrono.entities.crm.OpportunityStage;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OpportunityDTO {
    private final Long id;
    private final String title;
    private final OpportunityStage stage;
    private final BigDecimal value;
    private final Double probability;
    private final LocalDate expectedCloseDate;
    private final Long customerId;

    public OpportunityDTO(Long id, String title, OpportunityStage stage, BigDecimal value,
                          Double probability, LocalDate expectedCloseDate, Long customerId) {
        this.id = id;
        this.title = title;
        this.stage = stage;
        this.value = value;
        this.probability = probability;
        this.expectedCloseDate = expectedCloseDate;
        this.customerId = customerId;
    }

    public static OpportunityDTO from(Opportunity opportunity) {
        return new OpportunityDTO(
                opportunity.getId(),
                opportunity.getTitle(),
                opportunity.getStage(),
                opportunity.getValue(),
                opportunity.getProbability(),
                opportunity.getExpectedCloseDate(),
                opportunity.getCustomer() != null ? opportunity.getCustomer().getId() : null);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public OpportunityStage getStage() {
        return stage;
    }

    public BigDecimal getValue() {
        return value;
    }

    public Double getProbability() {
        return probability;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public Long getCustomerId() {
        return customerId;
    }
}
