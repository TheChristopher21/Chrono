package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.Product;

import java.math.BigDecimal;

public class ProductDTO {
    private final Long id;
    private final String sku;
    private final String name;
    private final String description;
    private final String unitOfMeasure;
    private final BigDecimal unitPrice;

    public ProductDTO(Long id, String sku, String name, String description, String unitOfMeasure, BigDecimal unitPrice) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.unitOfMeasure = unitOfMeasure;
        this.unitPrice = unitPrice;
    }

    public static ProductDTO from(Product product) {
        return new ProductDTO(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getUnitOfMeasure(),
                product.getUnitPrice());
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
