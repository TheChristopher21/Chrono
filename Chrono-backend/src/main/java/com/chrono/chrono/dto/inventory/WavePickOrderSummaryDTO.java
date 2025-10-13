package com.chrono.chrono.dto.inventory;

public class WavePickOrderSummaryDTO {

    private Long orderId;
    private String orderNumber;
    private String customerName;
    private int lineCount;
    private int units;
    private String status;

    public WavePickOrderSummaryDTO(Long orderId, String orderNumber, String customerName,
                                   int lineCount, int units, String status) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.lineCount = lineCount;
        this.units = units;
        this.status = status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getLineCount() {
        return lineCount;
    }

    public int getUnits() {
        return units;
    }

    public String getStatus() {
        return status;
    }
}
