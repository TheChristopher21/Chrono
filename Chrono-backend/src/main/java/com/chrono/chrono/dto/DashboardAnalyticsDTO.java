package com.chrono.chrono.dto;

public class DashboardAnalyticsDTO {
    private String username;
    private int overtimeMinutes;

    public DashboardAnalyticsDTO() {}

    public DashboardAnalyticsDTO(String username, int overtimeMinutes) {
        this.username = username;
        this.overtimeMinutes = overtimeMinutes;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getOvertimeMinutes() {
        return overtimeMinutes;
    }

    public void setOvertimeMinutes(int overtimeMinutes) {
        this.overtimeMinutes = overtimeMinutes;
    }
}
