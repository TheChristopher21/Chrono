package com.chrono.chrono.dto.inventory;

import java.math.BigDecimal;

public class SubmitCycleCountRequest {
    private BigDecimal countedQuantity;

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public void setCountedQuantity(BigDecimal countedQuantity) {
        this.countedQuantity = countedQuantity;
    }
}
