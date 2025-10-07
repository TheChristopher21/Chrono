package com.chrono.chrono.warehouse.model;

import java.time.Instant;

public class MovementLogEntry {

    private String id;
    private String productId;
    private String fromLocation;
    private String toLocation;
    private int quantity;
    private Instant timestamp;
    private String hash;

    public MovementLogEntry() {
    }

    public MovementLogEntry(String id, String productId, String fromLocation, String toLocation,
                             int quantity, Instant timestamp, String hash) {
        this.id = id;
        this.productId = productId;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.quantity = quantity;
        this.timestamp = timestamp;
        this.hash = hash;
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

    public String getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(String fromLocation) {
        this.fromLocation = fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public void setToLocation(String toLocation) {
        this.toLocation = toLocation;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
