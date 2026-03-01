package com.chrono.chrono.dto.inventory;

import java.util.List;

public class PlanWavePickRequest {

    private List<Long> salesOrderIds;
    private Integer maxOrdersPerWave;
    private Boolean includeDrafts;

    public List<Long> getSalesOrderIds() {
        return salesOrderIds;
    }

    public void setSalesOrderIds(List<Long> salesOrderIds) {
        this.salesOrderIds = salesOrderIds;
    }

    public Integer getMaxOrdersPerWave() {
        return maxOrdersPerWave;
    }

    public void setMaxOrdersPerWave(Integer maxOrdersPerWave) {
        this.maxOrdersPerWave = maxOrdersPerWave;
    }

    public Boolean getIncludeDrafts() {
        return includeDrafts;
    }

    public void setIncludeDrafts(Boolean includeDrafts) {
        this.includeDrafts = includeDrafts;
    }
}
