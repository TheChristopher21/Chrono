package com.chrono.chrono.dto.inventory;

import java.util.List;

public class AutoReplenishPlanDTO {

    private final String supplierId;
    private final String supplierName;
    private final int leadTimeDays;
    private final double supplierScore;
    private final PurchaseOrderDTO purchaseOrder;
    private final List<AutoReplenishItemDTO> items;
    private final List<AutoReplenishSupplierDTO> rankedSuppliers;

    public AutoReplenishPlanDTO(String supplierId,
                                String supplierName,
                                int leadTimeDays,
                                double supplierScore,
                                PurchaseOrderDTO purchaseOrder,
                                List<AutoReplenishItemDTO> items,
                                List<AutoReplenishSupplierDTO> rankedSuppliers) {
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.leadTimeDays = leadTimeDays;
        this.supplierScore = supplierScore;
        this.purchaseOrder = purchaseOrder;
        this.items = items;
        this.rankedSuppliers = rankedSuppliers;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public double getSupplierScore() {
        return supplierScore;
    }

    public PurchaseOrderDTO getPurchaseOrder() {
        return purchaseOrder;
    }

    public List<AutoReplenishItemDTO> getItems() {
        return items;
    }

    public List<AutoReplenishSupplierDTO> getRankedSuppliers() {
        return rankedSuppliers;
    }
}
