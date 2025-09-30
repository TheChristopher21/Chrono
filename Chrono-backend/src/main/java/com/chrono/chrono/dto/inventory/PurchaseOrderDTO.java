package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.PurchaseOrder;
import com.chrono.chrono.entities.inventory.PurchaseOrderLine;
import com.chrono.chrono.entities.inventory.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PurchaseOrderDTO {
    private final Long id;
    private final String orderNumber;
    private final String vendorName;
    private final PurchaseOrderStatus status;
    private final LocalDate expectedDate;
    private final BigDecimal totalAmount;
    private final List<PurchaseOrderLineDTO> lines;

    public PurchaseOrderDTO(Long id, String orderNumber, String vendorName, PurchaseOrderStatus status,
                            LocalDate expectedDate, BigDecimal totalAmount, List<PurchaseOrderLineDTO> lines) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.vendorName = vendorName;
        this.status = status;
        this.expectedDate = expectedDate;
        this.totalAmount = totalAmount;
        this.lines = lines;
    }

    public static PurchaseOrderDTO from(PurchaseOrder order) {
        List<PurchaseOrderLineDTO> lineDTOs = order.getLines() == null ? List.of() :
                order.getLines().stream().map(PurchaseOrderLineDTO::from).toList();
        return new PurchaseOrderDTO(
                order.getId(),
                order.getOrderNumber(),
                order.getVendorName(),
                order.getStatus(),
                order.getExpectedDate(),
                order.getTotalAmount(),
                lineDTOs);
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getVendorName() {
        return vendorName;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<PurchaseOrderLineDTO> getLines() {
        return lines;
    }

    public static class PurchaseOrderLineDTO {
        private final Long id;
        private final Long productId;
        private final BigDecimal quantity;
        private final BigDecimal unitCost;

        public PurchaseOrderLineDTO(Long id, Long productId, BigDecimal quantity, BigDecimal unitCost) {
            this.id = id;
            this.productId = productId;
            this.quantity = quantity;
            this.unitCost = unitCost;
        }

        public static PurchaseOrderLineDTO from(PurchaseOrderLine line) {
            return new PurchaseOrderLineDTO(
                    line.getId(),
                    line.getProduct() != null ? line.getProduct().getId() : null,
                    line.getQuantity(),
                    line.getUnitCost());
        }

        public Long getId() {
            return id;
        }

        public Long getProductId() {
            return productId;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitCost() {
            return unitCost;
        }
    }
}
