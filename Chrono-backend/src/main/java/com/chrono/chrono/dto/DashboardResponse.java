package com.chrono.chrono.dto;

import java.util.List;
import java.util.Map;

public class DashboardResponse {

    private String userName;
    // Für klassische Nutzer: tägliche Einträge
    private List<String> dailyEntries;
    // Für stundenbasierte Nutzer: Monatsübersicht (z. B. "2025-03" -> gearbeitete Minuten)
    private Map<String, Long> monthlyOverview;
    private String role;

    public DashboardResponse() {}

    // Konstruktor für tägliche Übersicht
    public DashboardResponse(String userName, List<String> dailyEntries, String role) {
        this.userName = userName;
        this.dailyEntries = dailyEntries;
        this.role = role;
    }

    // Konstruktor für stundenbasierte Übersicht
    public DashboardResponse(String userName, Map<String, Long> monthlyOverview, String role) {
        this.userName = userName;
        this.monthlyOverview = monthlyOverview;
        this.role = role;
    }

    // Getter und Setter
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public List<String> getDailyEntries() { return dailyEntries; }
    public void setDailyEntries(List<String> dailyEntries) { this.dailyEntries = dailyEntries; }

    public Map<String, Long> getMonthlyOverview() { return monthlyOverview; }
    public void setMonthlyOverview(Map<String, Long> monthlyOverview) { this.monthlyOverview = monthlyOverview; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
