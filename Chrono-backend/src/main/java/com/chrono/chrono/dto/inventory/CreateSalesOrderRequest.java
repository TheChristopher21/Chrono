package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.SalesOrder;
import com.chrono.chrono.entities.inventory.SalesOrderLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public class CreateSalesOrderRequest {
    @NotBlank @Size(max = 255) private String orderNumber;
    @NotBlank @Size(max = 255) private String customerName;
    private LocalDate dueDate;
    @NotEmpty @Valid private List<CreateSalesOrderRequestLine> lines;

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
        @NotNull private Long productId;
        @NotNull @DecimalMin(value = "0.0", inclusive = false) private java.math.BigDecimal quantity;
        @NotNull @DecimalMin("0.0") private java.math.BigDecimal unitPrice;

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
