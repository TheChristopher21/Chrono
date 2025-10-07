package com.chrono.chrono.warehouse.dto;

import java.util.List;

public class PickRouteResponse {

    public static class RouteWaypoint {
        private String locationId;
        private String productId;
        private double x;
        private double y;
        private double z;
        private double etaSeconds;

        public RouteWaypoint(String locationId, String productId, double x, double y, double z, double etaSeconds) {
            this.locationId = locationId;
            this.productId = productId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.etaSeconds = etaSeconds;
        }

        public String getLocationId() {
            return locationId;
        }

        public String getProductId() {
            return productId;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public double getEtaSeconds() {
            return etaSeconds;
        }
    }

    private List<RouteWaypoint> waypoints;
    private double totalDistance;
    private double estimatedDurationSeconds;

    public PickRouteResponse(List<RouteWaypoint> waypoints, double totalDistance, double estimatedDurationSeconds) {
        this.waypoints = waypoints;
        this.totalDistance = totalDistance;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
    }

    public List<RouteWaypoint> getWaypoints() {
        return waypoints;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public double getEstimatedDurationSeconds() {
        return estimatedDurationSeconds;
    }
}
