package com.chrono.chrono.dto.inventory;

import java.math.BigDecimal;
import java.util.List;

public class AutoReplenishResponse {

    private final List<AutoReplenishPlanDTO> plans;
    private final int evaluatedProducts;
    private final int replenishedSkus;
    private final int generatedPurchaseOrders;
    private final BigDecimal totalBudget;

    public AutoReplenishResponse(List<AutoReplenishPlanDTO> plans,
                                 int evaluatedProducts,
                                 int replenishedSkus,
                                 int generatedPurchaseOrders,
                                 BigDecimal totalBudget) {
        this.plans = plans;
        this.evaluatedProducts = evaluatedProducts;
        this.replenishedSkus = replenishedSkus;
        this.generatedPurchaseOrders = generatedPurchaseOrders;
        this.totalBudget = totalBudget;
    }

    public List<AutoReplenishPlanDTO> getPlans() {
        return plans;
    }

    public int getEvaluatedProducts() {
        return evaluatedProducts;
    }

    public int getReplenishedSkus() {
        return replenishedSkus;
    }

    public int getGeneratedPurchaseOrders() {
        return generatedPurchaseOrders;
    }

    public BigDecimal getTotalBudget() {
        return totalBudget;
    }
}
