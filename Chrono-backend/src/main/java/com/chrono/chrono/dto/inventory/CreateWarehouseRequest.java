package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.Warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateWarehouseRequest {
    @NotBlank @Size(max = 255) private String code;
    @NotBlank @Size(max = 255) private String name;
    @Size(max = 512) private String location;

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
