package com.chrono.chrono.warehouse.dto;

import java.math.BigDecimal;
import java.util.List;

public class ProductRequest {

    private String id;
    private String name;
    private String category;
    private Double weightKg;
    private Double volumeCubicM;
    private BigDecimal costPrice;
    private BigDecimal salesPrice;
    private List<String> attributes;

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

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Double getVolumeCubicM() {
        return volumeCubicM;
    }

    public void setVolumeCubicM(Double volumeCubicM) {
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

    public List<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<String> attributes) {
        this.attributes = attributes;
    }
}
