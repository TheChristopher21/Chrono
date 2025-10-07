package com.chrono.chrono.warehouse.dto;

public class CarrierLabelResponse {

    private String carrier;
    private String shipmentId;
    private String trackingNumber;
    private String label;

    public CarrierLabelResponse(String carrier, String shipmentId, String trackingNumber, String label) {
        this.carrier = carrier;
        this.shipmentId = shipmentId;
        this.trackingNumber = trackingNumber;
        this.label = label;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getLabel() {
        return label;
    }
}
