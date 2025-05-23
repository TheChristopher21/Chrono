package com.chrono.chrono.services;

import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DashboardService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    public DashboardResponse getUserDashboard(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Falls Roles leer sind, Default "NONE"
        String roleName = user.getRoles().isEmpty()
                ? "NONE"
                : user.getRoles().iterator().next().getRoleName();

        // Stundenbasierter Mitarbeiter => Monatliche Summen berechnen
        if (Boolean.TRUE.equals(user.getIsHourly())) {
            return buildHourlyDashboard(user, roleName);

        } else {
            // Klassische Tag-Übersicht
            return buildDailyDashboard(user, roleName);
        }
    }

    /**
     * Für stundenbasierte User:
     * Summiere alle gearbeiteten Minuten pro Monat (z.B. "2025-04" => 1234 min).
     */
    private DashboardResponse buildHourlyDashboard(User user, String roleName) {
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByDailyDateDesc(user);

        Map<String, Long> monthlyTotals = new HashMap<>();

        for (TimeTracking tt : list) {
            // gearbeiteten Minuten berechnen
            long minutesWorked = computeWorkedMinutes(tt);
            // Key = "YYYY-MM"
            String monthKey = tt.getDailyDate().getYear()
                    + "-"
                    + String.format("%02d", tt.getDailyDate().getMonthValue());
            monthlyTotals.put(
                    monthKey,
                    monthlyTotals.getOrDefault(monthKey, 0L) + minutesWorked
            );
        }

        // DashboardResponse kann z.B. "monthlyTotals" als Map<String,Long> enthalten
        return new DashboardResponse(user.getUsername(), monthlyTotals, roleName);
    }

    /**
     * Für nicht-stundenbasierte User:
     * Liste der Tage (z.B. "2025-05-06 (07:00-16:45 / Pause 12:00-12:45)").
     */
    private DashboardResponse buildDailyDashboard(User user, String roleName) {
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByDailyDateDesc(user);

        List<String> dailyEntries = new ArrayList<>();
        for (TimeTracking tt : list) {
            StringBuilder sb = new StringBuilder();
            sb.append(tt.getDailyDate().toString()).append(" ");

            LocalTime ws = tt.getWorkStart();
            LocalTime we = tt.getWorkEnd();
            LocalTime bs = tt.getBreakStart();
            LocalTime be = tt.getBreakEnd();

            sb.append("(");
            if (ws != null && we != null) {
                sb.append(ws).append("-").append(we);
            } else if (ws != null) {
                // z.B. noch kein WorkEnd
                sb.append(ws).append("-...");
            } else {
                sb.append("no times");
            }
            sb.append(")");

            // Pause anhängen
            if (bs != null && be != null) {
                sb.append(" Pause ").append(bs).append("-").append(be);
            }

            // Optional dailyNote
            if (tt.getDailyNote() != null && !tt.getDailyNote().trim().isEmpty()) {
                sb.append(" Note: ").append(tt.getDailyNote());
            }

            dailyEntries.add(sb.toString());
        }

        return new DashboardResponse(user.getUsername(), dailyEntries, roleName);
    }

    /**
     * Hilfsmethode: worked = (workEnd - workStart) - (breakEnd - breakStart)
     */
    private long computeWorkedMinutes(TimeTracking tt) {
        if (tt.getWorkStart() == null || tt.getWorkEnd() == null) {
            return 0L;
        }
        long total = ChronoUnit.MINUTES.between(tt.getWorkStart(), tt.getWorkEnd());
        if (tt.getBreakStart() != null && tt.getBreakEnd() != null) {
            total -= ChronoUnit.MINUTES.between(tt.getBreakStart(), tt.getBreakEnd());
        }
        // mindestens 0
        return Math.max(total, 0L);
    }
}
