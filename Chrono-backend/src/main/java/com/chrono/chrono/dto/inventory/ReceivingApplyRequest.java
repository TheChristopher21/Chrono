package com.chrono.chrono.dto.inventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReceivingApplyRequest {
    private Long warehouseId;
    private String reference;
    private Long purchaseOrderId;
    private boolean completePurchaseOrder;
    private List<ReceivingApplyItem> items = new ArrayList<>();

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
        private Long productId;
        private BigDecimal quantity;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }
    }
}
