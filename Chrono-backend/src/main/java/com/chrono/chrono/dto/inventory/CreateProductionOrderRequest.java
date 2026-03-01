package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.Product;
import com.chrono.chrono.entities.inventory.ProductionOrder;
import com.chrono.chrono.entities.inventory.ProductionOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateProductionOrderRequest {
    private String orderNumber;
    private Long productId;
    private BigDecimal quantity;
    private ProductionOrderStatus status;
    private LocalDate startDate;
    private LocalDate completionDate;

    public ProductionOrder toEntity(Product product) {
        ProductionOrder order = new ProductionOrder();
        order.setOrderNumber(orderNumber);
        order.setProduct(product);
        order.setQuantity(quantity);
        if (status != null) {
            order.setStatus(status);
        }
        if (startDate != null) {
            order.setStartDate(startDate);
        }
        if (completionDate != null) {
            order.setCompletionDate(completionDate);
        }
        return order;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

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

    public ProductionOrderStatus getStatus() {
        return status;
    }

    public void setStatus(ProductionOrderStatus status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
    }
}
