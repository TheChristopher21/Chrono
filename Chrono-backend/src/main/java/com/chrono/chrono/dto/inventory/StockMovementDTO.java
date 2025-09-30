package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.StockMovement;
import com.chrono.chrono.entities.inventory.StockMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StockMovementDTO {
    private final Long id;
    private final Long productId;
    private final Long warehouseId;
    private final StockMovementType type;
    private final BigDecimal quantityChange;
    private final String reference;
    private final LocalDateTime movementDate;

    public StockMovementDTO(Long id, Long productId, Long warehouseId, StockMovementType type,
                            BigDecimal quantityChange, String reference, LocalDateTime movementDate) {
        this.id = id;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.type = type;
        this.quantityChange = quantityChange;
        this.reference = reference;
        this.movementDate = movementDate;
    }

    public static StockMovementDTO from(StockMovement movement) {
        return new StockMovementDTO(
                movement.getId(),
                movement.getProduct() != null ? movement.getProduct().getId() : null,
                movement.getWarehouse() != null ? movement.getWarehouse().getId() : null,
                movement.getType(),
                movement.getQuantityChange(),
                movement.getReference(),
                movement.getMovementDate());
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public StockMovementType getType() {
        return type;
    }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public String getReference() {
        return reference;
    }

    public LocalDateTime getMovementDate() {
        return movementDate;
    }
}
