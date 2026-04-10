package com.chrono.chrono.dto.inventory;

import java.math.BigDecimal;
import java.util.List;

public class ReceivingPreviewResponse {
    private final boolean ready;
    private final String mode;
    private final String matchType;
    private final String message;
    private final String reference;
    private final String vendorName;
    private final String documentDate;
    private final Long purchaseOrderId;
    private final String purchaseOrderNumber;
    private final String purchaseOrderStatus;
    private final boolean canCompletePurchaseOrder;
    private final List<String> detectedCodes;
    private final List<String> warnings;
    private final String extractedTextSnippet;
    private final List<ReceivingPreviewItem> items;

    public ReceivingPreviewResponse(boolean ready,
                                    String mode,
                                    String matchType,
                                    String message,
                                    String reference,
                                    String vendorName,
                                    String documentDate,
                                    Long purchaseOrderId,
                                    String purchaseOrderNumber,
                                    String purchaseOrderStatus,
                                    boolean canCompletePurchaseOrder,
                                    List<String> detectedCodes,
                                    List<String> warnings,
                                    String extractedTextSnippet,
                                    List<ReceivingPreviewItem> items) {
        this.ready = ready;
        this.mode = mode;
        this.matchType = matchType;
        this.message = message;
        this.reference = reference;
        this.vendorName = vendorName;
        this.documentDate = documentDate;
        this.purchaseOrderId = purchaseOrderId;
        this.purchaseOrderNumber = purchaseOrderNumber;
        this.purchaseOrderStatus = purchaseOrderStatus;
        this.canCompletePurchaseOrder = canCompletePurchaseOrder;
        this.detectedCodes = detectedCodes;
        this.warnings = warnings;
        this.extractedTextSnippet = extractedTextSnippet;
        this.items = items;
    }

    public boolean isReady() {
        return ready;
    }

    public String getMode() {
        return mode;
    }

    public String getMatchType() {
        return matchType;
    }

    public String getMessage() {
        return message;
    }

    public String getReference() {
        return reference;
    }

    public String getVendorName() {
        return vendorName;
    }

    public String getDocumentDate() {
        return documentDate;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public String getPurchaseOrderNumber() {
        return purchaseOrderNumber;
    }

    public String getPurchaseOrderStatus() {
        return purchaseOrderStatus;
    }

    public boolean isCanCompletePurchaseOrder() {
        return canCompletePurchaseOrder;
    }

    public List<String> getDetectedCodes() {
        return detectedCodes;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public String getExtractedTextSnippet() {
        return extractedTextSnippet;
    }

    public List<ReceivingPreviewItem> getItems() {
        return items;
    }

    public static class ReceivingPreviewItem {
        private final Long productId;
        private final String sku;
        private final String productName;
        private final BigDecimal quantity;
        private final String unitOfMeasure;
        private final String source;

        public ReceivingPreviewItem(Long productId,
                                    String sku,
                                    String productName,
                                    BigDecimal quantity,
                                    String unitOfMeasure,
                                    String source) {
            this.productId = productId;
            this.sku = sku;
            this.productName = productName;
            this.quantity = quantity;
            this.unitOfMeasure = unitOfMeasure;
            this.source = source;
        }

        public Long getProductId() {
            return productId;
        }

        public String getSku() {
            return sku;
        }

        public String getProductName() {
            return productName;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public String getUnitOfMeasure() {
            return unitOfMeasure;
        }

        public String getSource() {
            return source;
        }
    }
}
