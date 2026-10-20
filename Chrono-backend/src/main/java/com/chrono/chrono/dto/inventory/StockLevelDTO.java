package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.StockLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.chrono.chrono.entities.inventory.InventoryStatus;

public class StockLevelDTO {
    private final Long id;
    private final ProductDTO product;
    private final WarehouseDTO warehouse;
    private final BigDecimal quantity;
    private final BigDecimal reservedQuantity;
    private final BigDecimal availableQuantity;
    private final WarehouseBinDTO warehouseBin;
    private final String lotNumber;
    private final String serialNumber;
    private final LocalDate expirationDate;
    private final InventoryStatus inventoryStatus;

    public StockLevelDTO(Long id, ProductDTO product, WarehouseDTO warehouse, BigDecimal quantity) {
        this(id, product, warehouse, quantity, BigDecimal.ZERO, quantity, null, null, null, null, InventoryStatus.AVAILABLE);
    }

    public StockLevelDTO(Long id,
                         ProductDTO product,
                         WarehouseDTO warehouse,
                         BigDecimal quantity,
                         BigDecimal reservedQuantity,
                         BigDecimal availableQuantity,
                         WarehouseBinDTO warehouseBin,
                         String lotNumber,
                         String serialNumber,
                         LocalDate expirationDate,
                         InventoryStatus inventoryStatus) {
        this.id = id;
        this.product = product;
        this.warehouse = warehouse;
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
        this.availableQuantity = availableQuantity;
        this.warehouseBin = warehouseBin;
        this.lotNumber = lotNumber;
        this.serialNumber = serialNumber;
        this.expirationDate = expirationDate;
        this.inventoryStatus = inventoryStatus;
    }

    public static StockLevelDTO from(StockLevel stockLevel) {
        return new StockLevelDTO(
                stockLevel.getId(),
                ProductDTO.from(stockLevel.getProduct()),
                WarehouseDTO.from(stockLevel.getWarehouse()),
                stockLevel.getQuantity(),
                stockLevel.getReservedQuantity(),
                stockLevel.getAvailableQuantity(),
                stockLevel.getWarehouseBin() == null ? null : WarehouseBinDTO.from(stockLevel.getWarehouseBin()),
                stockLevel.getLotNumber(),
                stockLevel.getSerialNumber(),
                stockLevel.getExpirationDate(),
                stockLevel.getInventoryStatus());
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

    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public WarehouseBinDTO getWarehouseBin() { return warehouseBin; }
    public String getLotNumber() { return lotNumber; }
    public String getSerialNumber() { return serialNumber; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public InventoryStatus getInventoryStatus() { return inventoryStatus; }
}
