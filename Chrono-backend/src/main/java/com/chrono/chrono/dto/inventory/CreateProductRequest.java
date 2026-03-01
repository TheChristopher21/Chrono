package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.Product;

import java.math.BigDecimal;

public class CreateProductRequest {
    private String sku;
    private String name;
    private String description;
    private String unitOfMeasure;
    private BigDecimal unitCost;
    private BigDecimal unitPrice;
    private boolean active = true;

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Product toEntity() {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription(description);
        if (unitOfMeasure != null) {
            product.setUnitOfMeasure(unitOfMeasure);
        }
        if (unitCost != null) {
            product.setUnitCost(unitCost);
        }
        if (unitPrice != null) {
            product.setUnitPrice(unitPrice);
        }
        product.setActive(active);
        return product;
    }
}
