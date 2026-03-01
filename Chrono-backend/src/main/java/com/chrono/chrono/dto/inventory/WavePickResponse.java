package com.chrono.chrono.dto.inventory;

import java.util.List;

public class WavePickResponse {

    private List<WavePickWaveDTO> waves;
    private int totalOrders;
    private int totalUnits;
    private int totalSkus;
    private double totalDistance;
    private double totalEstimatedDurationSeconds;
    private double averageUnitsPerWave;

    public WavePickResponse(List<WavePickWaveDTO> waves, int totalOrders, int totalUnits,
                            int totalSkus, double totalDistance, double totalEstimatedDurationSeconds,
                            double averageUnitsPerWave) {
        this.waves = waves;
        this.totalOrders = totalOrders;
        this.totalUnits = totalUnits;
        this.totalSkus = totalSkus;
        this.totalDistance = totalDistance;
        this.totalEstimatedDurationSeconds = totalEstimatedDurationSeconds;
        this.averageUnitsPerWave = averageUnitsPerWave;
    }

    public List<WavePickWaveDTO> getWaves() {
        return waves;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public int getTotalSkus() {
        return totalSkus;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public double getTotalEstimatedDurationSeconds() {
        return totalEstimatedDurationSeconds;
    }

    public double getAverageUnitsPerWave() {
        return averageUnitsPerWave;
    }
}
