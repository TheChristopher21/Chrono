package com.chrono.chrono.warehouse.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class ProductResponse {

    private String id;
    private String name;
    private String category;
    private double weightKg;
    private double volumeCubicM;
    private BigDecimal costPrice;
    private BigDecimal salesPrice;
    private String demandSegment;
    private Instant lastUpdated;
    private List<String> attributes;

    public ProductResponse(String id, String name, String category, double weightKg, double volumeCubicM,
                           BigDecimal costPrice, BigDecimal salesPrice, String demandSegment,
                           Instant lastUpdated, List<String> attributes) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.weightKg = weightKg;
        this.volumeCubicM = volumeCubicM;
        this.costPrice = costPrice;
        this.salesPrice = salesPrice;
        this.demandSegment = demandSegment;
        this.lastUpdated = lastUpdated;
        this.attributes = attributes;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public double getVolumeCubicM() {
        return volumeCubicM;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public BigDecimal getSalesPrice() {
        return salesPrice;
    }

    public String getDemandSegment() {
        return demandSegment;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public List<String> getAttributes() {
        return attributes;
    }
}
