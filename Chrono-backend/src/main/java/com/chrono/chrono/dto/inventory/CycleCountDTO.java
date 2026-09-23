package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.CycleCount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CycleCountDTO {
    private final Long id;
    private final String planNumber;
    private final Long productId;
    private final String productSku;
    private final String productName;
    private final Long warehouseId;
    private final String warehouseName;
    private final BigDecimal expectedQuantity;
    private final BigDecimal countedQuantity;
    private final BigDecimal variance;
    private final String status;
    private final String requestedBy;
    private final String countedBy;
    private final String approvedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime countedAt;
    private final LocalDateTime approvedAt;

    public CycleCountDTO(Long id,
                         String planNumber,
                         Long productId,
                         String productSku,
                         String productName,
                         Long warehouseId,
                         String warehouseName,
                         BigDecimal expectedQuantity,
                         BigDecimal countedQuantity,
                         BigDecimal variance,
                         String status,
                         String requestedBy,
                         String countedBy,
                         String approvedBy,
                         LocalDateTime createdAt,
                         LocalDateTime countedAt,
                         LocalDateTime approvedAt) {
        this.id = id;
        this.planNumber = planNumber;
        this.productId = productId;
        this.productSku = productSku;
        this.productName = productName;
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
        this.expectedQuantity = expectedQuantity;
        this.countedQuantity = countedQuantity;
        this.variance = variance;
        this.status = status;
        this.requestedBy = requestedBy;
        this.countedBy = countedBy;
        this.approvedBy = approvedBy;
        this.createdAt = createdAt;
        this.countedAt = countedAt;
        this.approvedAt = approvedAt;
    }

    public static CycleCountDTO from(CycleCount cycleCount) {
        return new CycleCountDTO(
                cycleCount.getId(),
                cycleCount.getPlanNumber(),
                cycleCount.getProduct() != null ? cycleCount.getProduct().getId() : null,
                cycleCount.getProduct() != null ? cycleCount.getProduct().getSku() : null,
                cycleCount.getProduct() != null ? cycleCount.getProduct().getName() : null,
                cycleCount.getWarehouse() != null ? cycleCount.getWarehouse().getId() : null,
                cycleCount.getWarehouse() != null ? cycleCount.getWarehouse().getName() : null,
                cycleCount.getExpectedQuantity(),
                cycleCount.getCountedQuantity(),
                cycleCount.getVariance(),
                cycleCount.getStatus() != null ? cycleCount.getStatus().name() : null,
                cycleCount.getRequestedBy(),
                cycleCount.getCountedBy(),
                cycleCount.getApprovedBy(),
                cycleCount.getCreatedAt(),
                cycleCount.getCountedAt(),
                cycleCount.getApprovedAt());
    }

    public Long getId() {
        return id;
    }

    public String getPlanNumber() {
        return planNumber;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public BigDecimal getExpectedQuantity() {
        return expectedQuantity;
    }

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public BigDecimal getVariance() {
        return variance;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getCountedBy() {
        return countedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCountedAt() {
        return countedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }
}
