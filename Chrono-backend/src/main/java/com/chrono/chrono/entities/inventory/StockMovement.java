package com.chrono.chrono.entities.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inv_stock_movements",
        uniqueConstraints = @UniqueConstraint(name = "uk_inv_stock_movements_idempotency", columnNames = {"idempotency_key"}),
        indexes = {
                @Index(name = "idx_inv_stock_movements_product_date", columnList = "product_id,movement_date"),
                @Index(name = "idx_inv_stock_movements_warehouse_date", columnList = "warehouse_id,movement_date"),
                @Index(name = "idx_inv_stock_movements_bin", columnList = "warehouse_bin_id")
        }
)
public class StockMovement {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantityChange;

    @Column(nullable = false)
    private LocalDateTime movementDate = LocalDateTime.now();

    @Column(length = 128)
    private String reference;

    @Column(length = 512)
    private String notes;

    @Column(name = "lot_number", length = 64)
    private String lotNumber;

    @Column(name = "serial_number", length = 64)
    private String serialNumber;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_status", nullable = false, length = 32)
    private InventoryStatus inventoryStatus = InventoryStatus.AVAILABLE;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

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

    public StockMovementType getType() {
        return type;
    }

    public void setType(StockMovementType type) {
        this.type = type;
    }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(BigDecimal quantityChange) {
        this.quantityChange = quantityChange;
    }

    public LocalDateTime getMovementDate() {
        return movementDate;
    }

    public void setMovementDate(LocalDateTime movementDate) {
        this.movementDate = movementDate;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

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

    public String getCreatedBy() { return createdBy; }

    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getIdempotencyKey() { return idempotencyKey; }

    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
