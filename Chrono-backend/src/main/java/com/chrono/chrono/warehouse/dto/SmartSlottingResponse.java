package com.chrono.chrono.warehouse.dto;

public class SmartSlottingResponse {

    private String locationId;
    private double confidence;
    private double walkingTimeSeconds;
    private String reason;

    public SmartSlottingResponse(String locationId, double confidence, double walkingTimeSeconds, String reason) {
        this.locationId = locationId;
        this.confidence = confidence;
        this.walkingTimeSeconds = walkingTimeSeconds;
        this.reason = reason;
    }

    public String getLocationId() {
        return locationId;
    }

    public double getConfidence() {
        return confidence;
    }

    public double getWalkingTimeSeconds() {
        return walkingTimeSeconds;
    }

    public String getReason() {
        return reason;
    }
}
