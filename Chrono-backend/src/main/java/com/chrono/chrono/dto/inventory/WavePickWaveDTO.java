package com.chrono.chrono.dto.inventory;

import java.util.List;

public class WavePickWaveDTO {

    private String waveId;
    private String zone;
    private List<WavePickOrderSummaryDTO> orders;
    private List<WavePickStopDTO> stops;
    private double totalDistance;
    private double estimatedDurationSeconds;
    private int totalUnits;
    private int totalSkus;
    private double unitsPerHour;

    public WavePickWaveDTO(String waveId, String zone, List<WavePickOrderSummaryDTO> orders,
                           List<WavePickStopDTO> stops, double totalDistance,
                           double estimatedDurationSeconds, int totalUnits, int totalSkus,
                           double unitsPerHour) {
        this.waveId = waveId;
        this.zone = zone;
        this.orders = orders;
        this.stops = stops;
        this.totalDistance = totalDistance;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
        this.totalUnits = totalUnits;
        this.totalSkus = totalSkus;
        this.unitsPerHour = unitsPerHour;
    }

    public String getWaveId() {
        return waveId;
    }

    public String getZone() {
        return zone;
    }

    public List<WavePickOrderSummaryDTO> getOrders() {
        return orders;
    }

    public List<WavePickStopDTO> getStops() {
        return stops;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public double getEstimatedDurationSeconds() {
        return estimatedDurationSeconds;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public int getTotalSkus() {
        return totalSkus;
    }

    public double getUnitsPerHour() {
        return unitsPerHour;
    }
}
