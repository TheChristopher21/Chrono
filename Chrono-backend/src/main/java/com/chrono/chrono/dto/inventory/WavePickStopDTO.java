package com.chrono.chrono.dto.inventory;

import java.util.List;

public class WavePickStopDTO {

    private String locationId;
    private String productSku;
    private int quantity;
    private double etaSeconds;
    private List<String> orderNumbers;

    public WavePickStopDTO(String locationId, String productSku, int quantity, double etaSeconds,
                           List<String> orderNumbers) {
        this.locationId = locationId;
        this.productSku = productSku;
        this.quantity = quantity;
        this.etaSeconds = etaSeconds;
        this.orderNumbers = orderNumbers;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getProductSku() {
        return productSku;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getEtaSeconds() {
        return etaSeconds;
    }

    public List<String> getOrderNumbers() {
        return orderNumbers;
    }
}
