package com.chrono.chrono.warehouse.dto;

public class SmartSlottingRequest {

    private String productId;
    private double weightKg;
    private double volumeCubicM;
    private String zonePreference;
    private int expectedTurnoverDays;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public double getVolumeCubicM() {
        return volumeCubicM;
    }

    public void setVolumeCubicM(double volumeCubicM) {
        this.volumeCubicM = volumeCubicM;
    }

    public String getZonePreference() {
        return zonePreference;
    }

    public void setZonePreference(String zonePreference) {
        this.zonePreference = zonePreference;
    }

    public int getExpectedTurnoverDays() {
        return expectedTurnoverDays;
    }

    public void setExpectedTurnoverDays(int expectedTurnoverDays) {
        this.expectedTurnoverDays = expectedTurnoverDays;
    }
}
