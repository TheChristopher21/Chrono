package com.chrono.chrono.dto.inventory;

import java.time.LocalDate;
import java.util.Map;

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
    private final int daysUntilStockout;
    private final double aiConfidence;
    private final String aiNarrative;
    private final Map<LocalDate, Integer> aiDailyForecast;

    public AutoReplenishItemDTO(Long productId,
                                String sku,
                                String productName,
                                int recommendedQuantity,
                                int projectedShortfall,
                                int safetyStock,
                                boolean stockOutRisk,
                                boolean overstockRisk,
                                double serviceLevelTarget,
                                String rationale,
                                int daysUntilStockout,
                                double aiConfidence,
                                String aiNarrative,
                                Map<LocalDate, Integer> aiDailyForecast) {
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
        this.daysUntilStockout = daysUntilStockout;
        this.aiConfidence = aiConfidence;
        this.aiNarrative = aiNarrative;
        this.aiDailyForecast = Map.copyOf(aiDailyForecast);
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

    public int getDaysUntilStockout() {
        return daysUntilStockout;
    }

    public double getAiConfidence() {
        return aiConfidence;
    }

    public String getAiNarrative() {
        return aiNarrative;
    }

    public Map<LocalDate, Integer> getAiDailyForecast() {
        return aiDailyForecast;
    }
}
