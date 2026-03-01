package com.chrono.chrono.dto.inventory;

import com.chrono.chrono.entities.inventory.ProductionOrder;
import com.chrono.chrono.entities.inventory.ProductionOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductionOrderDTO {
    private final Long id;
    private final String orderNumber;
    private final Long productId;
    private final String productName;
    private final BigDecimal quantity;
    private final ProductionOrderStatus status;
    private final LocalDate startDate;
    private final LocalDate completionDate;

    public ProductionOrderDTO(Long id,
                               String orderNumber,
                               Long productId,
                               String productName,
                               BigDecimal quantity,
                               ProductionOrderStatus status,
                               LocalDate startDate,
                               LocalDate completionDate) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.status = status;
        this.startDate = startDate;
        this.completionDate = completionDate;
    }

    public static ProductionOrderDTO from(ProductionOrder order) {
        return new ProductionOrderDTO(
                order.getId(),
                order.getOrderNumber(),
                order.getProduct() != null ? order.getProduct().getId() : null,
                order.getProduct() != null ? order.getProduct().getName() : null,
                order.getQuantity(),
                order.getStatus(),
                order.getStartDate(),
                order.getCompletionDate()
        );
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public ProductionOrderStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }
}
