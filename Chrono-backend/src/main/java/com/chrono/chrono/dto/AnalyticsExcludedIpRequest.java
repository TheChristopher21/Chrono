package com.chrono.chrono.dto;

public class AnalyticsExcludedIpRequest {
    private String ipAddress;
    private String label;

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
