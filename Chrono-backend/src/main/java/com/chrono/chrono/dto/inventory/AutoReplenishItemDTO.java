package com.chrono.chrono.dto.inventory;

public class AutoReplenishItemDTO {

    private final Long productId;
    private final String sku;
    private final String productName;
    private final int recommendedQuantity;
    private final int projectedShortfall;
    private final int safetyStock;
    private final boolean stockOutRisk;
    private final boolean overstockRisk;
    private final double serviceLevelTarget;
    private final String rationale;

    public AutoReplenishItemDTO(Long productId,
                                String sku,
                                String productName,
                                int recommendedQuantity,
                                int projectedShortfall,
                                int safetyStock,
                                boolean stockOutRisk,
                                boolean overstockRisk,
                                double serviceLevelTarget,
                                String rationale) {
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.recommendedQuantity = recommendedQuantity;
        this.projectedShortfall = projectedShortfall;
        this.safetyStock = safetyStock;
        this.stockOutRisk = stockOutRisk;
        this.overstockRisk = overstockRisk;
        this.serviceLevelTarget = serviceLevelTarget;
        this.rationale = rationale;
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

    public int getRecommendedQuantity() {
        return recommendedQuantity;
    }

    public int getProjectedShortfall() {
        return projectedShortfall;
    }

    public int getSafetyStock() {
        return safetyStock;
    }

    public boolean isStockOutRisk() {
        return stockOutRisk;
    }

    public boolean isOverstockRisk() {
        return overstockRisk;
    }

    public double getServiceLevelTarget() {
        return serviceLevelTarget;
    }

    public String getRationale() {
        return rationale;
    }
}
