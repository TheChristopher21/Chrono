package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.SalesOrder;
import com.chrono.chrono.entities.inventory.SalesOrderLine;
import com.chrono.chrono.entities.inventory.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class SalesOrderDTO {
    private final Long id;
    private final String orderNumber;
    private final String customerName;
    private final SalesOrderStatus status;
    private final LocalDate dueDate;
    private final BigDecimal totalAmount;
    private final List<SalesOrderLineDTO> lines;

    public SalesOrderDTO(Long id, String orderNumber, String customerName, SalesOrderStatus status,
                         LocalDate dueDate, BigDecimal totalAmount, List<SalesOrderLineDTO> lines) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.status = status;
        this.dueDate = dueDate;
        this.totalAmount = totalAmount;
        this.lines = lines;
    }

    public static SalesOrderDTO from(SalesOrder order) {
        List<SalesOrderLineDTO> lineDTOs = order.getLines() == null ? List.of() :
                order.getLines().stream().map(SalesOrderLineDTO::from).toList();
        return new SalesOrderDTO(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getStatus(),
                order.getDueDate(),
                order.getTotalAmount(),
                lineDTOs);
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public SalesOrderStatus getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<SalesOrderLineDTO> getLines() {
        return lines;
    }

    public static class SalesOrderLineDTO {
        private final Long id;
        private final Long productId;
        private final BigDecimal quantity;
        private final BigDecimal unitPrice;

        public SalesOrderLineDTO(Long id, Long productId, BigDecimal quantity, BigDecimal unitPrice) {
            this.id = id;
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public static SalesOrderLineDTO from(SalesOrderLine line) {
            return new SalesOrderLineDTO(
                    line.getId(),
                    line.getProduct() != null ? line.getProduct().getId() : null,
                    line.getQuantity(),
                    line.getUnitPrice());
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

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }
    }
}
