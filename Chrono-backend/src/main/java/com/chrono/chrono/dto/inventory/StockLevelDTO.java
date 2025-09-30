package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.StockLevel;

import java.math.BigDecimal;

public class StockLevelDTO {
    private final Long id;
    private final ProductDTO product;
    private final WarehouseDTO warehouse;
    private final BigDecimal quantity;

    public StockLevelDTO(Long id, ProductDTO product, WarehouseDTO warehouse, BigDecimal quantity) {
        this.id = id;
        this.product = product;
        this.warehouse = warehouse;
        this.quantity = quantity;
    }

    public static StockLevelDTO from(StockLevel stockLevel) {
        return new StockLevelDTO(
                stockLevel.getId(),
                ProductDTO.from(stockLevel.getProduct()),
                WarehouseDTO.from(stockLevel.getWarehouse()),
                stockLevel.getQuantity());
    }

    public Long getId() {
        return id;
    }

    public ProductDTO getProduct() {
        return product;
    }

    public WarehouseDTO getWarehouse() {
        return warehouse;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
