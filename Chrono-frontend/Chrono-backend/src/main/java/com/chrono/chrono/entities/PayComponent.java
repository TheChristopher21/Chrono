package com.chrono.chrono.entities;

import jakarta.persistence.Embeddable;

@Embeddable
public class PayComponent {
    private String type;
    private Double amount;

    public PayComponent() {}

    public PayComponent(String type, Double amount) {
        this.type = type;
        this.amount = amount;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}
