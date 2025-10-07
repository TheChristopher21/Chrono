package com.chrono.chrono.warehouse.dto;

import java.time.Instant;

public class ReturnWorkflowResponse {

    private String caseId;
    private String productId;
    private String reason;
    private String status;
    private Instant createdAt;

    public ReturnWorkflowResponse(String caseId, String productId, String reason, String status, Instant createdAt) {
        this.caseId = caseId;
        this.productId = productId;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getProductId() {
        return productId;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
