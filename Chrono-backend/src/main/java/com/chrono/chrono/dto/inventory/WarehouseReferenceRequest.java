package com.chrono.chrono.dto.inventory;

import jakarta.validation.constraints.NotNull;

public class WarehouseReferenceRequest {
    @NotNull private Long warehouseId;

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }
}
