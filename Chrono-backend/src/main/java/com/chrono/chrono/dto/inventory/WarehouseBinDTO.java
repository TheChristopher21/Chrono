package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.WarehouseBin;

import java.math.BigDecimal;

public record WarehouseBinDTO(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String code,
        String name,
        String zone,
        String aisle,
        String rack,
        String shelf,
        String barcode,
        Integer pickSequence,
        BigDecimal capacityQuantity,
        boolean active
) {
    public static WarehouseBinDTO from(WarehouseBin bin) {
        return new WarehouseBinDTO(
                bin.getId(),
                bin.getWarehouse().getId(),
                bin.getWarehouse().getCode(),
                bin.getCode(),
                bin.getName(),
                bin.getZone(),
                bin.getAisle(),
                bin.getRack(),
                bin.getShelf(),
                bin.getBarcode(),
                bin.getPickSequence(),
                bin.getCapacityQuantity(),
                bin.isActive()
        );
    }
}
