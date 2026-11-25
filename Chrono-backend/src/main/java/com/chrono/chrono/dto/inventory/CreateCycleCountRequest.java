package com.chrono.chrono.dto.inventory;

import jakarta.validation.constraints.NotNull;

public class CreateCycleCountRequest {
    @NotNull private Long productId;
    @NotNull private Long warehouseId;

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
}
