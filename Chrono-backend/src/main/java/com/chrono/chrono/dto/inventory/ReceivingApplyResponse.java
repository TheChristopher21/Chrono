package com.chrono.chrono.dto.inventory;

import java.math.BigDecimal;

public class ReceivingApplyResponse {
    private final String mode;
    private final String message;
    private final String reference;
    private final String purchaseOrderNumber;
    private final boolean purchaseOrderCompleted;
    private final int bookedItemCount;
    private final BigDecimal totalQuantity;

    public ReceivingApplyResponse(String mode,
                                  String message,
                                  String reference,
                                  String purchaseOrderNumber,
                                  boolean purchaseOrderCompleted,
                                  int bookedItemCount,
                                  BigDecimal totalQuantity) {
        this.mode = mode;
        this.message = message;
        this.reference = reference;
        this.purchaseOrderNumber = purchaseOrderNumber;
        this.purchaseOrderCompleted = purchaseOrderCompleted;
        this.bookedItemCount = bookedItemCount;
        this.totalQuantity = totalQuantity;
    }

    public String getMode() {
        return mode;
    }

    public String getMessage() {
        return message;
    }

    public String getReference() {
        return reference;
    }

    public String getPurchaseOrderNumber() {
        return purchaseOrderNumber;
    }

    public boolean isPurchaseOrderCompleted() {
        return purchaseOrderCompleted;
    }

    public int getBookedItemCount() {
        return bookedItemCount;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }
}
