package com.chrono.chrono.warehouse.dto;

public class MobileInboundRequest {

    private String productId;
    private int quantity;
    private String dockLocationId;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getDockLocationId() {
        return dockLocationId;
    }

    public void setDockLocationId(String dockLocationId) {
        this.dockLocationId = dockLocationId;
    }
}
