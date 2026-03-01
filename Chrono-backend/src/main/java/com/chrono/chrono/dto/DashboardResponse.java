package com.chrono.chrono.dto;

import java.util.List;
import java.util.Map;

public class DashboardResponse {

    private String username;
    private String roleName;

    // NEU: Dieses Feld wird von allen Dashboards (Hourly, Percentage, Standard) erwartet.
    // Es enthält die detaillierten Tageszusammenfassungen für den angefragten Zeitraum.
    private List<DailyTimeSummaryDTO> dailySummaries;

    // Die folgenden Felder sind veraltet, da die neue Logik im DashboardService
    // immer eine Liste von DailyTimeSummaryDTOs zurückgeben sollte.
    // Sie werden vorerst für Abwärtskompatibilität beibehalten.
    private Map<String, Long> monthlyTotals;  // für stundenbasierte (veraltete Logik)
    private List<String> dailyEntries;        // für nicht-stundenbasierte (veraltete Logik)

    // NEU: Ein leerer Konstruktor macht das Erstellen des Objekts im Service flexibler.
    public DashboardResponse() {
    }

    // Bestehende Konstruktoren (optional beibehalten)
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

    // --- NEUE GETTER UND SETTER ---
    public List<DailyTimeSummaryDTO> getDailySummaries() {
        return dailySummaries;
    }

    public void setDailySummaries(List<DailyTimeSummaryDTO> dailySummaries) {
        this.dailySummaries = dailySummaries;
    }


    // --- Bestehende Getter und Setter ---
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