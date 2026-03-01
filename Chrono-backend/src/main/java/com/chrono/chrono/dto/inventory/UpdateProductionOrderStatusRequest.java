package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.ProductionOrderStatus;

import java.time.LocalDate;

public class UpdateProductionOrderStatusRequest {
    private ProductionOrderStatus status;
    private LocalDate startDate;
    private LocalDate completionDate;

    public ProductionOrderStatus getStatus() {
        return status;
    }

    public void setStatus(ProductionOrderStatus status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
    }
}
