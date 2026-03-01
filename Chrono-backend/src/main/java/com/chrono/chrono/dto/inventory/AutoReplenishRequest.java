package com.chrono.chrono.dto.inventory;

import java.util.List;

public class AutoReplenishRequest {

    public static class SupplierPreference {
        private String supplierId;
        private Double weightPrice;
        private Double weightReliability;
        private Double weightSustainability;

        public String getSupplierId() {
            return supplierId;
        }

        public void setSupplierId(String supplierId) {
            this.supplierId = supplierId;
        }

        public Double getWeightPrice() {
            return weightPrice;
        }

        public void setWeightPrice(Double weightPrice) {
            this.weightPrice = weightPrice;
        }

        public Double getWeightReliability() {
            return weightReliability;
        }

        public void setWeightReliability(Double weightReliability) {
            this.weightReliability = weightReliability;
        }

        public Double getWeightSustainability() {
            return weightSustainability;
        }

        public void setWeightSustainability(Double weightSustainability) {
            this.weightSustainability = weightSustainability;
        }
    }

    private List<Long> productIds;
    private Integer planningHorizonDays;
    private Integer safetyDays;
    private Double serviceLevelTarget;
    private List<SupplierPreference> supplierPreferences;

    public List<Long> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<Long> productIds) {
        this.productIds = productIds;
    }

    public Integer getPlanningHorizonDays() {
        return planningHorizonDays;
    }

    public void setPlanningHorizonDays(Integer planningHorizonDays) {
        this.planningHorizonDays = planningHorizonDays;
    }

    public Integer getSafetyDays() {
        return safetyDays;
    }

    public void setSafetyDays(Integer safetyDays) {
        this.safetyDays = safetyDays;
    }

    public Double getServiceLevelTarget() {
        return serviceLevelTarget;
    }

    public void setServiceLevelTarget(Double serviceLevelTarget) {
        this.serviceLevelTarget = serviceLevelTarget;
    }

    public List<SupplierPreference> getSupplierPreferences() {
        return supplierPreferences;
    }

    public void setSupplierPreferences(List<SupplierPreference> supplierPreferences) {
        this.supplierPreferences = supplierPreferences;
    }
}
