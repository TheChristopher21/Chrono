package com.chrono.chrono.warehouse.model;

import java.time.Instant;

public class ReturnCase {

    private String id;
    private String productId;
    private String reason;
    private String status;
    private Instant createdAt;

    public ReturnCase() {
    }

    public ReturnCase(String id, String productId, String reason, String status, Instant createdAt) {
        this.id = id;
        this.productId = productId;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
