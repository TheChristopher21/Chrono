package com.chrono.chrono.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class SubmitCycleCountRequest {
    @NotNull @DecimalMin("0.0") private BigDecimal countedQuantity;

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public void setCountedQuantity(BigDecimal countedQuantity) {
        this.countedQuantity = countedQuantity;
    }
}
