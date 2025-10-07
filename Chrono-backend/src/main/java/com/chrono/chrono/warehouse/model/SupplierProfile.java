package com.chrono.chrono.warehouse.model;

public class SupplierProfile {

    private String id;
    private String name;
    private double priceScore;
    private double reliabilityScore;
    private double sustainabilityScore;
    private int leadTimeDays;

    public SupplierProfile() {
    }

    public SupplierProfile(String id, String name, double priceScore, double reliabilityScore,
                            double sustainabilityScore, int leadTimeDays) {
        this.id = id;
        this.name = name;
        this.priceScore = priceScore;
        this.reliabilityScore = reliabilityScore;
        this.sustainabilityScore = sustainabilityScore;
        this.leadTimeDays = leadTimeDays;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPriceScore() {
        return priceScore;
    }

    public void setPriceScore(double priceScore) {
        this.priceScore = priceScore;
    }

    public double getReliabilityScore() {
        return reliabilityScore;
    }

    public void setReliabilityScore(double reliabilityScore) {
        this.reliabilityScore = reliabilityScore;
    }

    public double getSustainabilityScore() {
        return sustainabilityScore;
    }

    public void setSustainabilityScore(double sustainabilityScore) {
        this.sustainabilityScore = sustainabilityScore;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public void setLeadTimeDays(int leadTimeDays) {
        this.leadTimeDays = leadTimeDays;
    }
}
