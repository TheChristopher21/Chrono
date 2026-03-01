package com.chrono.chrono.warehouse.dto;

import java.util.List;

public class BoxRecommendationRequest {

    public static class Item {
        private String productId;
        private Integer quantity;
        private Double lengthCm;
        private Double widthCm;
        private Double heightCm;
        private Double weightKg;
        private Double volumeCubicM;

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public Double getLengthCm() {
            return lengthCm;
        }

        public void setLengthCm(Double lengthCm) {
            this.lengthCm = lengthCm;
        }

        public Double getWidthCm() {
            return widthCm;
        }

        public void setWidthCm(Double widthCm) {
            this.widthCm = widthCm;
        }

        public Double getHeightCm() {
            return heightCm;
        }

        public void setHeightCm(Double heightCm) {
            this.heightCm = heightCm;
        }

        public Double getWeightKg() {
            return weightKg;
        }

        public void setWeightKg(Double weightKg) {
            this.weightKg = weightKg;
        }

        public Double getVolumeCubicM() {
            return volumeCubicM;
        }

        public void setVolumeCubicM(Double volumeCubicM) {
            this.volumeCubicM = volumeCubicM;
        }
    }

    private List<Item> items;
    private Double targetUtilisation;

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public Double getTargetUtilisation() {
        return targetUtilisation;
    }

    public void setTargetUtilisation(Double targetUtilisation) {
        this.targetUtilisation = targetUtilisation;
    }
}
