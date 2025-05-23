package com.chrono.chrono.dto;

import java.util.List;
import java.util.Map;

public class DashboardResponse {

    private String username;
    private Map<String, Long> monthlyTotals;  // für stundenbasierte
    private List<String> dailyEntries;        // für nicht-stundenbasierte
    private String roleName;

    // Konstruktoren, z.B. Overloads:
    public DashboardResponse(String username, Map<String, Long> monthlyTotals, String roleName) {
        this.username = username;
        this.monthlyTotals = monthlyTotals;
        this.roleName = roleName;
    }

    public DashboardResponse(String username, List<String> dailyEntries, String roleName) {
        this.username = username;
        this.dailyEntries = dailyEntries;
        this.roleName = roleName;
    }


    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getDailyEntries() {
        return dailyEntries;
    }

    public void setDailyEntries(List<String> dailyEntries) {
        this.dailyEntries = dailyEntries;
    }

    public Map<String, Long> getMonthlyTotals() {
        return monthlyTotals;
    }

    public void setMonthlyTotals(Map<String, Long> monthlyTotals) {
        this.monthlyTotals = monthlyTotals;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
