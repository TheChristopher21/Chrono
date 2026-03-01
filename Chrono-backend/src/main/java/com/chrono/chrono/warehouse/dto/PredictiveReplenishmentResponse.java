package com.chrono.chrono.warehouse.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PredictiveReplenishmentResponse {

    private final String productId;
    private final double averageDailyDemand;
    private final double demandTrend;
    private final double demandVolatility;
    private final int reorderPoint;
    private final int recommendedOrderQuantity;
    private final int daysUntilStockout;
    private final double confidence;
    private final String rationale;
    private final Map<LocalDate, Integer> dailyDemandForecast;

    public PredictiveReplenishmentResponse(String productId,
                                           double averageDailyDemand,
                                           double demandTrend,
                                           double demandVolatility,
                                           int reorderPoint,
                                           int recommendedOrderQuantity,
                                           int daysUntilStockout,
                                           double confidence,
                                           String rationale,
                                           Map<LocalDate, Integer> dailyDemandForecast) {
        this.productId = productId;
        this.averageDailyDemand = averageDailyDemand;
        this.demandTrend = demandTrend;
        this.demandVolatility = demandVolatility;
        this.reorderPoint = reorderPoint;
        this.recommendedOrderQuantity = recommendedOrderQuantity;
        this.daysUntilStockout = daysUntilStockout;
        this.confidence = confidence;
        this.rationale = rationale;
        this.dailyDemandForecast = Collections.unmodifiableMap(new LinkedHashMap<>(dailyDemandForecast));
    }

    public String getProductId() {
        return productId;
    }

    public double getAverageDailyDemand() {
        return averageDailyDemand;
    }

    public double getDemandTrend() {
        return demandTrend;
    }

    public double getDemandVolatility() {
        return demandVolatility;
    }

    public int getReorderPoint() {
        return reorderPoint;
    }

    public int getRecommendedOrderQuantity() {
        return recommendedOrderQuantity;
    }

    public int getDaysUntilStockout() {
        return daysUntilStockout;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getRationale() {
        return rationale;
    }

    public Map<LocalDate, Integer> getDailyDemandForecast() {
        return dailyDemandForecast;
    }
}
