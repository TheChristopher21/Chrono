package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.InventoryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReceivingApplyRequest {
    @NotNull private Long warehouseId;
    @Size(max = 128)
    private String reference;
    private Long purchaseOrderId;
    private boolean completePurchaseOrder;
    @NotEmpty @Valid private List<ReceivingApplyItem> items = new ArrayList<>();

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(Long purchaseOrderId) {
        this.purchaseOrderId = purchaseOrderId;
    }

    public boolean isCompletePurchaseOrder() {
        return completePurchaseOrder;
    }

    public void setCompletePurchaseOrder(boolean completePurchaseOrder) {
        this.completePurchaseOrder = completePurchaseOrder;
    }

    public List<ReceivingApplyItem> getItems() {
        return items;
    }

    public void setItems(List<ReceivingApplyItem> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public static class ReceivingApplyItem {
        @NotNull private Long productId;
        private Long warehouseBinId;
        @NotNull @DecimalMin(value = "0.0", inclusive = false) private BigDecimal quantity;
        @Size(max = 64) private String lotNumber;
        @Size(max = 64) private String serialNumber;
        private LocalDate expirationDate;
        private InventoryStatus inventoryStatus = InventoryStatus.AVAILABLE;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Long getWarehouseBinId() { return warehouseBinId; }

        public void setWarehouseBinId(Long warehouseBinId) { this.warehouseBinId = warehouseBinId; }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public String getLotNumber() { return lotNumber; }
        public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }
        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
        public LocalDate getExpirationDate() { return expirationDate; }
        public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }
        public InventoryStatus getInventoryStatus() { return inventoryStatus; }
        public void setInventoryStatus(InventoryStatus inventoryStatus) { this.inventoryStatus = inventoryStatus; }
    }
}
