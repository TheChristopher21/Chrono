package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.Warehouse;

public class CreateWarehouseRequest {
    private String code;
    private String name;
    private String location;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Warehouse toEntity() {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setLocation(location);
        return warehouse;
    }
}
