package com.chrono.chrono.entities.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "inv_stock_levels",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inv_stock_levels_inventory_key",
                columnNames = {"product_id", "warehouse_id", "inventory_key"}
        ),
        indexes = {
                @Index(name = "idx_inv_stock_levels_warehouse_bin", columnList = "warehouse_id,warehouse_bin_id"),
                @Index(name = "idx_inv_stock_levels_expiration", columnList = "expiration_date,inventory_status")
        }
)
public class StockLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_bin_id")
    private WarehouseBin warehouseBin;

    @Column(name = "inventory_key", length = 320)
    private String inventoryKey;

    @Column(name = "lot_number", length = 64)
    private String lotNumber;

    @Column(name = "serial_number", length = 64)
    private String serialNumber;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_status", nullable = false, length = 32)
    private InventoryStatus inventoryStatus = InventoryStatus.AVAILABLE;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reserved_quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Version
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public WarehouseBin getWarehouseBin() { return warehouseBin; }

    public void setWarehouseBin(WarehouseBin warehouseBin) { this.warehouseBin = warehouseBin; }

    public String getInventoryKey() { return inventoryKey; }

    public void setInventoryKey(String inventoryKey) { this.inventoryKey = inventoryKey; }

    public String getLotNumber() { return lotNumber; }

    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public String getSerialNumber() { return serialNumber; }

    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public LocalDate getExpirationDate() { return expirationDate; }

    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }

    public InventoryStatus getInventoryStatus() { return inventoryStatus; }

    public void setInventoryStatus(InventoryStatus inventoryStatus) { this.inventoryStatus = inventoryStatus; }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getReservedQuantity() { return reservedQuantity; }

    public void setReservedQuantity(BigDecimal reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public BigDecimal getAvailableQuantity() {
        return quantity.subtract(reservedQuantity == null ? BigDecimal.ZERO : reservedQuantity);
    }

    public Long getVersion() { return version; }
}
