package com.chrono.chrono.services;

import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    public DashboardResponse getUserDashboard(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Prüfe, ob der Nutzer stundenbasiert arbeitet (isHourly == true)
        if (Boolean.TRUE.equals(user.getIsHourly())) {
            List<TimeTracking> entries = timeTrackingRepository.findByUserOrderByStartTimeDesc(user);
            Map<String, Long> monthlyTotals = new HashMap<>();
            for (TimeTracking tt : entries) {
                if (tt.getEndTime() != null) {
                    long minutesWorked = Duration.between(tt.getStartTime(), tt.getEndTime()).toMinutes();
                    // Schlüssel im Format "YYYY-MM" (z. B. "2025-03")
                    String monthKey = tt.getStartTime().getYear() + "-" + String.format("%02d", tt.getStartTime().getMonthValue());
                    monthlyTotals.put(monthKey, monthlyTotals.getOrDefault(monthKey, 0L) + minutesWorked);
                }
            }
            String roleName = user.getRoles().isEmpty() ? "NONE" : user.getRoles().iterator().next().getRoleName();
            return new DashboardResponse(user.getUsername(), monthlyTotals, roleName);
        } else {
            // Klassische tägliche Übersicht
            List<String> dailyEntries = timeTrackingRepository.findByUserOrderByStartTimeDesc(user)
                    .stream()
                    .map(tt -> tt.getStartTime() + " - " + (tt.getEndTime() == null ? "..." : tt.getEndTime()))
                    .collect(Collectors.toList());
            String roleName = user.getRoles().isEmpty() ? "NONE" : user.getRoles().iterator().next().getRoleName();
            return new DashboardResponse(user.getUsername(), dailyEntries, roleName);
        }
    }
}
