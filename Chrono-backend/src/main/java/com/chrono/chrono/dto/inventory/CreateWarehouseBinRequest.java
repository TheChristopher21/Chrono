package com.chrono.chrono.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateWarehouseBinRequest {
    @NotBlank
    @Size(max = 64)
    private String code;
    @NotBlank
    @Size(max = 160)
    private String name;
    @Size(max = 64)
    private String zone;
    @Size(max = 64)
    private String aisle;
    @Size(max = 64)
    private String rack;
    @Size(max = 64)
    private String shelf;
    @Size(max = 128)
    private String barcode;
    private Integer pickSequence = 0;
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal capacityQuantity;

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
}
