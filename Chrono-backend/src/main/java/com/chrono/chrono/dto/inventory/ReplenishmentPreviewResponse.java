package com.chrono.chrono.dto.inventory;

import java.util.List;

public class ReplenishmentPreviewResponse {

    private final List<AutoReplenishItemDTO> items;
    private final int evaluatedProducts;
    private final int productsRequiringReplenishment;

    public ReplenishmentPreviewResponse(List<AutoReplenishItemDTO> items,
                                        int evaluatedProducts,
                                        int productsRequiringReplenishment) {
        this.items = items;
        this.evaluatedProducts = evaluatedProducts;
        this.productsRequiringReplenishment = productsRequiringReplenishment;
    }

    public List<AutoReplenishItemDTO> getItems() {
        return items;
    }

    public int getEvaluatedProducts() {
        return evaluatedProducts;
    }

    public int getProductsRequiringReplenishment() {
        return productsRequiringReplenishment;
    }
}
