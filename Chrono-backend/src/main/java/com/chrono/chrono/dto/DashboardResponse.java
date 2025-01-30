package com.chrono.chrono.dto;

import java.util.List;

public class DashboardResponse {

    private String userName;
    private List<String> timeEntries; // Beispiel: könnte man anpassen
    private String role;
    // etc. je nach Bedarf

    public DashboardResponse() {
    }

    public DashboardResponse(String userName, List<String> timeEntries, String role) {
        this.userName = userName;
        this.timeEntries = timeEntries;
        this.role = role;
    }

    // Getter / Setter
    public String getUserName() {
        return userName;
    }

    public List<String> getTimeEntries() {
        return timeEntries;
    }

    public String getRole() {
        return role;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setTimeEntries(List<String> timeEntries) {
        this.timeEntries = timeEntries;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
