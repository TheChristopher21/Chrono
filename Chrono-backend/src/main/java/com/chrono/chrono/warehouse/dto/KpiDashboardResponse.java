package com.chrono.chrono.warehouse.dto;

import java.util.Map;

public class KpiDashboardResponse {

    private Map<String, Double> kpis;
    private Map<String, Double> trends;

    public KpiDashboardResponse(Map<String, Double> kpis, Map<String, Double> trends) {
        this.kpis = kpis;
        this.trends = trends;
    }

    public Map<String, Double> getKpis() {
        return kpis;
    }

    public Map<String, Double> getTrends() {
        return trends;
    }
}
