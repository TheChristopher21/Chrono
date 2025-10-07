package com.chrono.chrono.warehouse.dto;

import java.util.List;

public class SmartSourcingRequest {

    public static class SupplierPreference {
        private String supplierId;
        private double weightPrice;
        private double weightReliability;
        private double weightSustainability;

        public String getSupplierId() {
            return supplierId;
        }

        public void setSupplierId(String supplierId) {
            this.supplierId = supplierId;
        }

        public double getWeightPrice() {
            return weightPrice;
        }

        public void setWeightPrice(double weightPrice) {
            this.weightPrice = weightPrice;
        }

        public double getWeightReliability() {
            return weightReliability;
        }

        public void setWeightReliability(double weightReliability) {
            this.weightReliability = weightReliability;
        }

        public double getWeightSustainability() {
            return weightSustainability;
        }

        public void setWeightSustainability(double weightSustainability) {
            this.weightSustainability = weightSustainability;
        }
    }

    private String productId;
    private int quantity;
    private List<SupplierPreference> preferences;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public List<SupplierPreference> getPreferences() {
        return preferences;
    }

    public void setPreferences(List<SupplierPreference> preferences) {
        this.preferences = preferences;
    }
}
