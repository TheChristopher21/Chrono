package com.chrono.chrono.warehouse.dto;

import java.util.List;

public class PickRouteRequest {

    public static class PickItem {
        private String productId;
        private int quantity;

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
    }

    private List<PickItem> items;

    public List<PickItem> getItems() {
        return items;
    }

    public void setItems(List<PickItem> items) {
        this.items = items;
    }
}
