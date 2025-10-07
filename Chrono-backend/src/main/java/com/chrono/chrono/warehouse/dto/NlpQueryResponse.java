package com.chrono.chrono.warehouse.dto;

import java.util.Map;

public class NlpQueryResponse {

    private String query;
    private String interpretedMetric;
    private Map<String, Object> data;
    private String explanation;

    public NlpQueryResponse(String query, String interpretedMetric, Map<String, Object> data, String explanation) {
        this.query = query;
        this.interpretedMetric = interpretedMetric;
        this.data = data;
        this.explanation = explanation;
    }

    public String getQuery() {
        return query;
    }

    public String getInterpretedMetric() {
        return interpretedMetric;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getExplanation() {
        return explanation;
    }
}
