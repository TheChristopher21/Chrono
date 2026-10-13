package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.StockMovement;
import com.chrono.chrono.entities.inventory.StockMovementType;
import com.chrono.chrono.entities.inventory.InventoryStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockMovementDTO {
    private final Long id;
    private final Long productId;
    private final Long warehouseId;
    private final Long warehouseBinId;
    private final String warehouseBinCode;
    private final StockMovementType type;
    private final BigDecimal quantityChange;
    private final String reference;
    private final LocalDateTime movementDate;
    private final String lotNumber;
    private final String serialNumber;
    private final LocalDate expirationDate;
    private final InventoryStatus inventoryStatus;
    private final String notes;
    private final String createdBy;

    public StockMovementDTO(Long id, Long productId, Long warehouseId, StockMovementType type,
                            Long warehouseBinId, String warehouseBinCode, BigDecimal quantityChange,
                            String reference, LocalDateTime movementDate, String lotNumber,
                            String serialNumber, LocalDate expirationDate, InventoryStatus inventoryStatus,
                            String notes, String createdBy) {
        this.id = id;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.warehouseBinId = warehouseBinId;
        this.warehouseBinCode = warehouseBinCode;
        this.type = type;
        this.quantityChange = quantityChange;
        this.reference = reference;
        this.movementDate = movementDate;
        this.lotNumber = lotNumber;
        this.serialNumber = serialNumber;
        this.expirationDate = expirationDate;
        this.inventoryStatus = inventoryStatus;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public static StockMovementDTO from(StockMovement movement) {
        return new StockMovementDTO(
                movement.getId(),
                movement.getProduct() != null ? movement.getProduct().getId() : null,
                movement.getWarehouse() != null ? movement.getWarehouse().getId() : null,
                movement.getType(),
                movement.getWarehouseBin() != null ? movement.getWarehouseBin().getId() : null,
                movement.getWarehouseBin() != null ? movement.getWarehouseBin().getCode() : null,
                movement.getQuantityChange(),
                movement.getReference(),
                movement.getMovementDate(),
                movement.getLotNumber(),
                movement.getSerialNumber(),
                movement.getExpirationDate(),
                movement.getInventoryStatus(),
                movement.getNotes(),
                movement.getCreatedBy());
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

    public Long getWarehouseBinId() { return warehouseBinId; }

    public String getWarehouseBinCode() { return warehouseBinCode; }

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

    public String getLotNumber() {
        return lotNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public InventoryStatus getInventoryStatus() { return inventoryStatus; }

    public String getNotes() { return notes; }

    public String getCreatedBy() { return createdBy; }
}
