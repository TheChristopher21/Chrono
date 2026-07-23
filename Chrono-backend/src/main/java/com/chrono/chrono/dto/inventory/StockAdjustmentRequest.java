package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.StockMovementType;
import com.chrono.chrono.entities.inventory.InventoryStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class StockAdjustmentRequest {
    @NotNull private Long productId;
    @NotNull private Long warehouseId;
    private Long warehouseBinId;
    @NotNull @Digits(integer = 15, fraction = 4) private BigDecimal quantityChange;
    @NotNull private StockMovementType type;
    @Size(max = 128)
    private String reference;
    @Size(max = 512)
    private String notes;
    @Size(max = 64)
    private String lotNumber;
    @Size(max = 64)
    private String serialNumber;
    private LocalDate expirationDate;
    private InventoryStatus inventoryStatus = InventoryStatus.AVAILABLE;
    @Size(max = 160)
    private String idempotencyKey;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getWarehouseBinId() { return warehouseBinId; }

    public void setWarehouseBinId(Long warehouseBinId) { this.warehouseBinId = warehouseBinId; }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(BigDecimal quantityChange) {
        this.quantityChange = quantityChange;
    }

    public StockMovementType getType() {
        return type;
    }

    public void setType(StockMovementType type) {
        this.type = type;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getNotes() { return notes; }

    public void setNotes(String notes) { this.notes = notes; }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public InventoryStatus getInventoryStatus() { return inventoryStatus; }

    public void setInventoryStatus(InventoryStatus inventoryStatus) { this.inventoryStatus = inventoryStatus; }

    public String getIdempotencyKey() { return idempotencyKey; }

    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
