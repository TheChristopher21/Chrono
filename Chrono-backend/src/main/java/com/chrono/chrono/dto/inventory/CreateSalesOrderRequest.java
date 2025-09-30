package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.SalesOrder;
import com.chrono.chrono.entities.inventory.SalesOrderLine;

import java.time.LocalDate;
import java.util.List;

public class CreateSalesOrderRequest {
    private String orderNumber;
    private String customerName;
    private LocalDate dueDate;
    private List<CreateSalesOrderRequestLine> lines;

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public List<CreateSalesOrderRequestLine> getLines() {
        return lines;
    }

    public void setLines(List<CreateSalesOrderRequestLine> lines) {
        this.lines = lines;
    }

    public SalesOrder toEntity(List<SalesOrderLine> resolvedLines) {
        SalesOrder order = new SalesOrder();
        order.setOrderNumber(orderNumber);
        order.setCustomerName(customerName);
        order.setDueDate(dueDate);
        order.setLines(resolvedLines);
        return order;
    }

    public static class CreateSalesOrderRequestLine {
        private Long productId;
        private java.math.BigDecimal quantity;
        private java.math.BigDecimal unitPrice;

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

        public java.math.BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(java.math.BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }
    }
}
