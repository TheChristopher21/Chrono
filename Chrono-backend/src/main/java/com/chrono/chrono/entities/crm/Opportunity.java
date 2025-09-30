package com.chrono.chrono.entities.crm;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "crm_opportunities")
public class Opportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    private Customer customer;

    private String title;

    @Column(name = "opportunity_value", precision = 19, scale = 4)
    private BigDecimal value = BigDecimal.ZERO;

    private LocalDate expectedCloseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OpportunityStage stage = OpportunityStage.QUALIFICATION;

    private Double probability;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public void setExpectedCloseDate(LocalDate expectedCloseDate) {
        this.expectedCloseDate = expectedCloseDate;
    }

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
}
