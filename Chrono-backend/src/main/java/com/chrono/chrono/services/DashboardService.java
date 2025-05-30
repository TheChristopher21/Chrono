package com.chrono.chrono.services;

import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User; //
import com.chrono.chrono.repositories.TimeTrackingRepository; //
import com.chrono.chrono.repositories.UserRepository; //
import org.springframework.beans.factory.annotation.Autowired; //
import org.springframework.stereotype.Service; //

import java.time.LocalTime; //
import java.time.temporal.ChronoUnit; //
import java.util.*; //
import java.time.format.DateTimeFormatter;
import java.util.Locale;


@Service
public class DashboardService {

    @Autowired
    private UserRepository userRepository; //

    @Autowired
    private TimeTrackingRepository timeTrackingRepository; //

    @Autowired // HolidayService injizieren
    private HolidayService holidayService;

    public DashboardResponse getUserDashboard(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username)); //

        String roleName = user.getRoles().isEmpty()
                ? "NONE"
                : user.getRoles().iterator().next().getRoleName(); //

        if (Boolean.TRUE.equals(user.getIsHourly())) { //
            return buildHourlyDashboard(user, roleName); //

        } else {
            return buildDailyDashboard(user, roleName); //
        }
    }

    private DashboardResponse buildHourlyDashboard(User user, String roleName) {
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByDailyDateDesc(user); //
        Map<String, Long> monthlyTotals = new HashMap<>(); //

        for (TimeTracking tt : list) { //
            long minutesWorked = computeWorkedMinutes(tt); //
            String monthKey = tt.getDailyDate().getYear() //
                    + "-"
                    + String.format("%02d", tt.getDailyDate().getMonthValue()); //
            monthlyTotals.put( //
                    monthKey,
                    monthlyTotals.getOrDefault(monthKey, 0L) + minutesWorked //
            );
        }
        return new DashboardResponse(user.getUsername(), monthlyTotals, roleName); //
    }

    private DashboardResponse buildDailyDashboard(User user, String roleName) {
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByDailyDateDesc(user); //
        List<String> dailyEntries = new ArrayList<>(); //

        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.GERMAN);

        for (TimeTracking tt : list) { //
            StringBuilder sb = new StringBuilder(); //
            sb.append(tt.getDailyDate().format(dateFormatter)); // Formatierung angepasst

            boolean isHoliday = holidayService.isHoliday(tt.getDailyDate(), cantonAbbreviation);

            if (isHoliday) {
                String holidayName = holidayService.getHolidayName(tt.getDailyDate(), tt.getDailyDate().getYear(), cantonAbbreviation);
                sb.append(" (").append(holidayName).append(")");
                // An Feiertagen normalerweise keine Arbeitszeiten anzeigen, es sei denn, es wurde explizit gestempelt.
                // Wenn gestempelt wurde, kann man die Zeiten trotzdem anzeigen oder eine spezielle Logik implementieren.
                // Für den Moment: Wenn Feiertag, keine Zeiten anzeigen, außer es gibt eine Notiz.
            }

            // Zeiten nur anzeigen, wenn es kein Feiertag ist ODER wenn trotz Feiertag gestempelt wurde
            // (und der Feiertag nicht als komplett arbeitsfrei im Soll berücksichtigt wurde).
            // Da das Soll an Feiertagen 0 ist, ist eine Stempelung an einem Feiertag reine Überzeit.
            if (!isHoliday || tt.getWorkStart() != null) { // Zeiten anzeigen, wenn gestempelt oder kein Feiertag
                LocalTime ws = tt.getWorkStart(); //
                LocalTime we = tt.getWorkEnd(); //
                LocalTime bs = tt.getBreakStart(); //
                LocalTime be = tt.getBreakEnd(); //

                sb.append(" ("); //
                if (ws != null && we != null) { //
                    sb.append(ws).append("-").append(we); //
                } else if (ws != null) { //
                    sb.append(ws).append("-..."); //
                } else if (!isHoliday) { // Nur "keine Zeiten" anzeigen, wenn es kein Feiertag ist
                    sb.append("keine Zeiten"); //
                }
                sb.append(")"); //

                if (bs != null && be != null) { //
                    sb.append(" Pause ").append(bs).append("-").append(be); //
                }
            }


            if (tt.getDailyNote() != null && !tt.getDailyNote().trim().isEmpty()) { //
                sb.append(" Notiz: ").append(tt.getDailyNote()); //
            }
            dailyEntries.add(sb.toString()); //
        }
        return new DashboardResponse(user.getUsername(), dailyEntries, roleName); //
    }

    private long computeWorkedMinutes(TimeTracking tt) {
        if (tt.getWorkStart() == null || tt.getWorkEnd() == null) { //
            return 0L; //
        }
        long total = ChronoUnit.MINUTES.between(tt.getWorkStart(), tt.getWorkEnd()); //
        if (tt.getBreakStart() != null && tt.getBreakEnd() != null) { //
            total -= ChronoUnit.MINUTES.between(tt.getBreakStart(), tt.getBreakEnd()); //
        }
        return Math.max(total, 0L); //
    }
}