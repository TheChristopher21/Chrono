package com.chrono.chrono.dto.inventory;

public class AutoReplenishSupplierDTO {

    private final String supplierId;
    private final String supplierName;
    private final double score;
    private final int leadTimeDays;

    public AutoReplenishSupplierDTO(String supplierId, String supplierName, double score, int leadTimeDays) {
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
