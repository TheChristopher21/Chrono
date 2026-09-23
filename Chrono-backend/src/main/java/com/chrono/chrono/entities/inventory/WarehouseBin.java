package com.chrono.chrono.entities.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inv_warehouse_bins",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inv_warehouse_bins_warehouse_code", columnNames = {"warehouse_id", "code"}),
                @UniqueConstraint(name = "uk_inv_warehouse_bins_barcode", columnNames = {"barcode"})
        },
        indexes = {
                @Index(name = "idx_inv_warehouse_bins_warehouse", columnList = "warehouse_id"),
                @Index(name = "idx_inv_warehouse_bins_zone_sequence", columnList = "warehouse_id,zone,pick_sequence")
        }
)
public class WarehouseBin {

    public static final String DEFAULT_CODE = "DEFAULT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 64)
    private String zone;

    @Column(length = 64)
    private String aisle;

    @Column(length = 64)
    private String rack;

    @Column(length = 64)
    private String shelf;

    @Column(length = 128)
    private String barcode;

    @Column(name = "pick_sequence", nullable = false)
    private Integer pickSequence = 0;

    @Column(name = "capacity_quantity", precision = 19, scale = 4)
    private BigDecimal capacityQuantity;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getAisle() { return aisle; }
    public void setAisle(String aisle) { this.aisle = aisle; }
    public String getRack() { return rack; }
    public void setRack(String rack) { this.rack = rack; }
    public String getShelf() { return shelf; }
    public void setShelf(String shelf) { this.shelf = shelf; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public Integer getPickSequence() { return pickSequence; }
    public void setPickSequence(Integer pickSequence) { this.pickSequence = pickSequence; }
    public BigDecimal getCapacityQuantity() { return capacityQuantity; }
    public void setCapacityQuantity(BigDecimal capacityQuantity) { this.capacityQuantity = capacityQuantity; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getVersion() { return version; }
}
