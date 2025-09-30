package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.PurchaseOrder;
import com.chrono.chrono.entities.inventory.PurchaseOrderLine;

import java.time.LocalDate;
import java.util.List;

public class CreatePurchaseOrderRequest {
    private String orderNumber;
    private String vendorName;
    private LocalDate expectedDate;
    private List<CreatePurchaseOrderRequestLine> lines;

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
    }

    public List<CreatePurchaseOrderRequestLine> getLines() {
        return lines;
    }

    public void setLines(List<CreatePurchaseOrderRequestLine> lines) {
        this.lines = lines;
    }

    public PurchaseOrder toEntity(List<PurchaseOrderLine> resolvedLines) {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNumber(orderNumber);
        order.setVendorName(vendorName);
        order.setExpectedDate(expectedDate);
        order.setLines(resolvedLines);
        return order;
    }

    public static class CreatePurchaseOrderRequestLine {
        private Long productId;
        private java.math.BigDecimal quantity;
        private java.math.BigDecimal unitCost;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public java.math.BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(java.math.BigDecimal quantity) {
            this.quantity = quantity;
        }

        public java.math.BigDecimal getUnitCost() {
            return unitCost;
        }

        public void setUnitCost(java.math.BigDecimal unitCost) {
            this.unitCost = unitCost;
        }
    }
}
