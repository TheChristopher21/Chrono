package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateProductRequest {
    @NotBlank @Size(max = 255) private String sku;
    @NotBlank @Size(max = 255) private String name;
    @Size(max = 1024) private String description;
    @Size(max = 32) private String unitOfMeasure;
    @DecimalMin("0.0") private BigDecimal unitCost;
    @DecimalMin("0.0") private BigDecimal unitPrice;
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
