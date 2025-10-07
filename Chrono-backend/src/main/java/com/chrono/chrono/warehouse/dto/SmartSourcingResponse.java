package com.chrono.chrono.warehouse.dto;

import java.util.List;

public class SmartSourcingResponse {

    public static class SupplierScore {
        private String supplierId;
        private String supplierName;
        private double score;
        private int leadTimeDays;

        public SupplierScore(String supplierId, String supplierName, double score, int leadTimeDays) {
            this.supplierId = supplierId;
            this.supplierName = supplierName;
            this.score = score;
            this.leadTimeDays = leadTimeDays;
        }

        public String getSupplierId() {
            return supplierId;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public double getScore() {
            return score;
        }

        public int getLeadTimeDays() {
            return leadTimeDays;
        }
    }

    private String productId;
    private int requestedQuantity;
    private SupplierScore recommended;
    private List<SupplierScore> rankedSuppliers;

    public SmartSourcingResponse(String productId, int requestedQuantity, SupplierScore recommended,
                                 List<SupplierScore> rankedSuppliers) {
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.recommended = recommended;
        this.rankedSuppliers = rankedSuppliers;
    }

    public String getProductId() {
        return productId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public SupplierScore getRecommended() {
        return recommended;
    }

    public List<SupplierScore> getRankedSuppliers() {
        return rankedSuppliers;
    }
}
