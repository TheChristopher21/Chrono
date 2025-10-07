package com.chrono.chrono.warehouse.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class InventoryItem {

    private String productId;
    private String locationId;
    private int quantity;
    private String batchNumber;
    private List<String> serialNumbers = new ArrayList<>();
    private String lifecycleStatus;
    private Instant lastMovement;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public List<String> getSerialNumbers() {
        return serialNumbers;
    }

    public void setSerialNumbers(List<String> serialNumbers) {
        this.serialNumbers = serialNumbers;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public Instant getLastMovement() {
        return lastMovement;
    }

    public void setLastMovement(Instant lastMovement) {
        this.lastMovement = lastMovement;
    }
}
