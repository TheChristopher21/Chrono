package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TimeTrackingService timeTrackingService;
    @Autowired
    private HolidayService holidayService;

    public DashboardResponse getUserDashboard(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        String roleName = user.getRoles().isEmpty() ? "NONE" : user.getRoles().iterator().next().getRoleName();

        if (Boolean.TRUE.equals(user.getIsHourly())) {
            return buildHourlyDashboard(user, roleName);
        } else {
            return buildDailyDashboard(user, roleName);
        }
    }

    private DashboardResponse buildHourlyDashboard(User user, String roleName) {
        List<DailyTimeSummaryDTO> history = timeTrackingService.getUserHistory(user.getUsername());
        Map<String, Long> monthlyTotals = new HashMap<>();
        for (DailyTimeSummaryDTO summary : history) {
            long minutesWorked = summary.getWorkedMinutes();
            String monthKey = summary.getDate().getYear() + "-" + String.format("%02d", summary.getDate().getMonthValue());
            monthlyTotals.put(monthKey, monthlyTotals.getOrDefault(monthKey, 0L) + minutesWorked);
        }
        return new DashboardResponse(user.getUsername(), monthlyTotals, roleName);
    }

    private DashboardResponse buildDailyDashboard(User user, String roleName) {
        List<DailyTimeSummaryDTO> history = timeTrackingService.getUserHistory(user.getUsername());
        List<String> dailyDisplayEntries = new ArrayList<>();
        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.GERMAN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (DailyTimeSummaryDTO summary : history) {
            StringBuilder sb = new StringBuilder();
            sb.append(summary.getDate().format(dateFormatter));

            boolean isHoliday = holidayService.isHoliday(summary.getDate(), cantonAbbreviation);
            if (isHoliday) {
                sb.append(" (").append(holidayService.getHolidayName(summary.getDate(), summary.getDate().getYear(), cantonAbbreviation)).append(")");
            }

            DailyTimeSummaryDTO.PrimaryTimes pt = summary.getPrimaryTimes();
            LocalTime firstStart = pt.getFirstStartTime();
            LocalTime lastEnd = pt.getLastEndTime();

            if (firstStart != null) {
                sb.append(" (").append(firstStart.format(timeFormatter));
                if (lastEnd != null) {
                    sb.append("-").append(lastEnd.format(timeFormatter));
                } else if (pt.isOpen()){
                    sb.append("-OFFEN");
                } else {
                     sb.append("-?");
                }
                sb.append(")");
            } else if (!isHoliday) {
                sb.append(" (keine Zeiten)");
            }
            
            if (summary.getBreakMinutes() > 0) {
                sb.append(" Pause ").append(summary.getBreakMinutes()).append(" min");
            }

            if (summary.isNeedsCorrection()) {
                 sb.append(" [KORREKTUR ERFORDERLICH!]");
            }
            
            if (summary.getDailyNote() != null && !summary.getDailyNote().trim().isEmpty()) {
                sb.append(" Notiz: ").append(summary.getDailyNote());
            }
            dailyDisplayEntries.add(sb.toString());
        }
        return new DashboardResponse(user.getUsername(), dailyDisplayEntries, roleName);
    }
}
