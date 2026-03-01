package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.Warehouse;

public class WarehouseDTO {
    private final Long id;
    private final String code;
    private final String name;
    private final String location;

    public WarehouseDTO(Long id, String code, String name, String location) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.location = location;
    }

    public static WarehouseDTO from(Warehouse warehouse) {
        return new WarehouseDTO(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getLocation());
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }
}
