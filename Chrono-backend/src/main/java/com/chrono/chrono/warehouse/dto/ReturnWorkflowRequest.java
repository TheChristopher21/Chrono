package com.chrono.chrono.warehouse.dto;

public class ReturnWorkflowRequest {

    private String productId;
    private String reason;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
