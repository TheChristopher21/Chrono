package com.chrono.chrono.warehouse.model;

import java.time.Instant;

public class SensorReading {

    private String locationId;
    private double temperature;
    private double humidity;
    private double weight;
    private Instant timestamp;

    public SensorReading() {
    }

    public SensorReading(String locationId, double temperature, double humidity, double weight, Instant timestamp) {
        this.locationId = locationId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.weight = weight;
        this.timestamp = timestamp;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getHumidity() {
        return humidity;
    }

    public void setHumidity(double humidity) {
        this.humidity = humidity;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
