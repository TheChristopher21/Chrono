// src/main/java/com/chrono/service/DashboardService.java

package com.chrono.chrono.services;

import com.chrono.chrono.dto.DailyTimeSummaryDTO;
import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate; // Importieren
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TimeTrackingService timeTrackingService;

    // HolidayService wird nicht mehr im DashboardResponse benötigt, aber im TimeTrackingService

    // NEUE METHODE, die vom Controller aufgerufen wird
    public DashboardResponse getUserDashboardForWeek(String username, LocalDate startDate, LocalDate endDate) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        String roleName = user.getRoles().isEmpty() ? "NONE" : user.getRoles().iterator().next().getRoleName();

        // Holt ALLE Zusammenfassungen des Benutzers
        List<DailyTimeSummaryDTO> allSummaries = timeTrackingService.getUserHistory(username);

        // Filtert die Zusammenfassungen auf den gewünschten Zeitraum
        List<DailyTimeSummaryDTO> weekSummaries = allSummaries.stream()
                .filter(summary -> !summary.getDate().isBefore(startDate) && !summary.getDate().isAfter(endDate))
                .collect(Collectors.toList());

        // Erstellt die Response mit den korrekten Daten
        // WICHTIG: Ein neuer Konstruktor in DashboardResponse.java ist eventuell nötig,
        // oder Sie erstellen ein neues DTO speziell für diese Antwort.
        // Für eine schnelle Lösung, nehmen wir an, es gibt einen passenden Konstruktor oder Setter.
        DashboardResponse response = new DashboardResponse();
        response.setUsername(user.getUsername());
        response.setRoleName(roleName);
        response.setDailySummaries(weekSummaries); // Setzt das Feld, das das Frontend erwartet

        // Wenn Sie auch Monatssummen für Stundenlöhner brauchen, können Sie diese hier berechnen
        if (Boolean.TRUE.equals(user.getIsHourly())) {
            long totalMinutesInWeek = weekSummaries.stream()
                    .mapToLong(DailyTimeSummaryDTO::getWorkedMinutes)
                    .sum();
            // Sie können dies zur Response hinzufügen, falls nötig, z.B. response.setWeeklyTotalMinutes(totalMinutesInWeek);
        }

        return response;
    }

    // Die alte getUserDashboard Methode und die build... Methoden können entfernt oder angepasst werden,
    // wenn sie nicht mehr anderweitig gebraucht werden.
}