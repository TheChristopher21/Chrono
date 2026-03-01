package com.chrono.chrono.warehouse.dto;

import java.util.List;

public class MobileInboundResponse {

    public static class NavigationStep {
        private String from;
        private String to;
        private double distanceMeters;

        public NavigationStep(String from, String to, double distanceMeters) {
            this.from = from;
            this.to = to;
            this.distanceMeters = distanceMeters;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public double getDistanceMeters() {
            return distanceMeters;
        }
    }

    private String assignedLocationId;
    private List<NavigationStep> navigation;

    public MobileInboundResponse(String assignedLocationId, List<NavigationStep> navigation) {
        this.assignedLocationId = assignedLocationId;
        this.navigation = navigation;
    }

    public String getAssignedLocationId() {
        return assignedLocationId;
    }

    public List<NavigationStep> getNavigation() {
        return navigation;
    }
}
