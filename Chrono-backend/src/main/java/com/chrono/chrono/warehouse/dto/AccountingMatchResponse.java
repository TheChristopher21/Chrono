package com.chrono.chrono.warehouse.dto;

import java.math.BigDecimal;

public class AccountingMatchResponse {

    private String purchaseOrderId;
    private boolean autoApproved;
    private BigDecimal deviation;
    private String message;

    public AccountingMatchResponse(String purchaseOrderId, boolean autoApproved, BigDecimal deviation, String message) {
        this.purchaseOrderId = purchaseOrderId;
        this.autoApproved = autoApproved;
        this.deviation = deviation;
        this.message = message;
    }

    public String getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public boolean isAutoApproved() {
        return autoApproved;
    }

    public BigDecimal getDeviation() {
        return deviation;
    }

    public String getMessage() {
        return message;
    }
}
