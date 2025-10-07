package com.chrono.chrono.warehouse.model;

import java.util.Objects;

public class WarehouseLocation {

    private String id;
    private String zone;
    private double x;
    private double y;
    private double z;
    private int capacity;
    private int occupied;
    private boolean blocked;

    public WarehouseLocation() {
    }

    public WarehouseLocation(String id, String zone, double x, double y, double z, int capacity) {
        this.id = id;
        this.zone = zone;
        this.x = x;
        this.y = y;
        this.z = z;
        this.capacity = capacity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getOccupied() {
        return occupied;
    }

    public void setOccupied(int occupied) {
        this.occupied = occupied;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public double occupancyRate() {
        return capacity == 0 ? 0 : (double) occupied / capacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WarehouseLocation that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
