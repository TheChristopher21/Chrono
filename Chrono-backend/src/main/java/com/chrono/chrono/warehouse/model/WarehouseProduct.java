package com.chrono.chrono.warehouse.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain model that represents a product in the Chrono 2.0 warehouse module.
 * It intentionally avoids persistence annotations because the current
 * implementation keeps the data in memory to provide a lightweight reference
 * implementation that can easily be extended to a full persistence layer.
 */
public class WarehouseProduct {

    private String id;
    private String name;
    private String category;
    private double weightKg;
    private double volumeCubicM;
    private BigDecimal costPrice;
    private BigDecimal salesPrice;
    private String demandSegment;
    private Instant lastUpdated;
    private final List<String> attributes = new ArrayList<>();

    public WarehouseProduct() {
    }

    public WarehouseProduct(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public double getVolumeCubicM() {
        return volumeCubicM;
    }

    public void setVolumeCubicM(double volumeCubicM) {
        this.volumeCubicM = volumeCubicM;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getSalesPrice() {
        return salesPrice;
    }

    public void setSalesPrice(BigDecimal salesPrice) {
        this.salesPrice = salesPrice;
    }

    public String getDemandSegment() {
        return demandSegment;
    }

    public void setDemandSegment(String demandSegment) {
        this.demandSegment = demandSegment;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public void addAttribute(String attribute) {
        if (attribute != null && !attribute.isBlank()) {
            attributes.add(attribute.trim());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WarehouseProduct that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
