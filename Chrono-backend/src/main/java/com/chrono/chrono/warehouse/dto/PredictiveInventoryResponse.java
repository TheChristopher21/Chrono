package com.chrono.chrono.warehouse.dto;

import java.time.LocalDate;
import java.util.Map;

public class PredictiveInventoryResponse {

    private String productId;
    private Map<LocalDate, Integer> forecast;
    private boolean stockOutRisk;
    private boolean overstockRisk;

    public PredictiveInventoryResponse(String productId, Map<LocalDate, Integer> forecast,
                                       boolean stockOutRisk, boolean overstockRisk) {
        this.productId = productId;
        this.forecast = forecast;
        this.stockOutRisk = stockOutRisk;
        this.overstockRisk = overstockRisk;
    }

    public String getProductId() {
        return productId;
    }

    public Map<LocalDate, Integer> getForecast() {
        return forecast;
    }

    public boolean isStockOutRisk() {
        return stockOutRisk;
    }

    public boolean isOverstockRisk() {
        return overstockRisk;
    }
}
