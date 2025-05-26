package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserScheduleRule;
import com.chrono.chrono.entities.VacationRequest; // NEUER Import
import com.chrono.chrono.repositories.UserScheduleRuleRepository;
import org.slf4j.Logger; // NEUER Import für Logging
import org.slf4j.LoggerFactory; // NEUER Import für Logging
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

@Service
public class WorkScheduleService {

    public static final Logger logger = LoggerFactory.getLogger(WorkScheduleService.class); // NEU: Logger Instanz

    @Autowired
    private UserScheduleRuleRepository ruleRepo;

    private double applyPercentage(User user, double hours) {
        if (Boolean.TRUE.equals(user.getIsPercentage())
                && user.getWorkPercentage() != null) {
            return hours * user.getWorkPercentage() / 100.0;
        }
        return hours;
    }

    public double getExpectedWorkHours(User user, LocalDate date) {
        double baseHours;
        if (user.getScheduleEffectiveDate() != null && date.isBefore(user.getScheduleEffectiveDate())) {
            baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
        } else {
            List<Map<String, Double>> schedule = user.getWeeklySchedule();
            if (schedule != null && user.getScheduleCycle() != null && !schedule.isEmpty()) {
                try {
                    int cycleLength = user.getScheduleCycle();
                    // Als Referenz für den Wochenzyklus-Start nehmen wir einen festen Montag, z.B. den ersten Montag 2020
                    // Dies stellt sicher, dass der cycleIndex immer konsistent ist.
                    LocalDate epochMonday = LocalDate.of(2020, 1, 6); // Erster Montag im Januar 2020
                    LocalDate startOfWeekForDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    long weeksSinceEpoch = ChronoUnit.WEEKS.between(epochMonday, startOfWeekForDate);

                    int cycleIndex = (int) (weeksSinceEpoch % cycleLength);

                    if (cycleIndex < 0) { // Für Daten vor der Epoche
                        cycleIndex += cycleLength;
                    }

                    // Sicherstellen, dass der cycleIndex innerhalb der Listengröße liegt.
                    // schedule.size() ist die Anzahl der Wochen im Zyklus.
                    if (cycleIndex >= schedule.size()) {
                        logger.warn("Cycle index {} for user {} is out of bounds for schedule size {}. Using fallback. Date: {}",
                                cycleIndex, user.getUsername(), schedule.size(), date);
                        // Fallback, wenn Konfiguration fehlerhaft (sollte nicht passieren)
                        baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
                    } else {
                        Map<String, Double> weekSchedule = schedule.get(cycleIndex);
                        String dayOfWeekKey = date.getDayOfWeek().name().toLowerCase(); // z.B. "monday"
                        baseHours = weekSchedule.getOrDefault(dayOfWeekKey, 0.0); // Standard 0, wenn für den Tag nichts im Plan steht
                    }
                } catch (Exception ex) {
                    logger.error("Error processing weekly schedule for user {}: {}. Falling back to dailyWorkHours or default.", user.getUsername(), ex.getMessage(), ex);
                    baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
                }
            } else {
                // Fallback, wenn kein detaillierter Wochenplan vorhanden
                baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
            }
        }
        // Der Prozentsatz wird hier auf die tagesspezifische Vollzeit-Basis angewendet.
        return applyPercentage(user, baseHours);
    }

    public int getExpectedWeeklyMinutesForPercentageUser(User user, LocalDate dateInWeek, List<VacationRequest> approvedVacationsInWeek) {
        if (!Boolean.TRUE.equals(user.getIsPercentage()) || user.getWorkPercentage() == null) {
            // Diese Methode ist nur für prozentuale Mitarbeiter mit definiertem Prozentsatz gedacht.
            // Für andere Typen oder fehlenden Prozentsatz wird 0 zurückgegeben oder ein Fehler geworfen.
            return 0;
        }

        // Basis-Wochenstunden für 100% - dies sollte idealerweise aus einer Konfiguration kommen
        // oder als Durchschnitt des hinterlegten Wochenplans für eine 100%-Stelle berechnet werden.
        // Hier vereinfacht: 42.5 Stunden als 100%-Basis.
        double baseFullTimeWeeklyHours = 42.5;

        // Berechne das prozentuale Wochensoll in Stunden
        double percentageWeeklyHours = baseFullTimeWeeklyHours * (user.getWorkPercentage() / 100.0);
        int expectedWeeklyMinutes = (int) Math.round(percentageWeeklyHours * 60);

        // Reduziere das Wochensoll um genehmigte Urlaubstage in dieser Woche
        int vacationMinutesToDeduct = 0;
        LocalDate startOfWeek = dateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        for (VacationRequest vr : approvedVacationsInWeek) {
            // Es werden nur Urlaube berücksichtigt, die auch wirklich genehmigt sind.
            if (vr.isApproved()) {
                LocalDate vacStart = vr.getStartDate();
                LocalDate vacEnd = vr.getEndDate();

                for (LocalDate d = vacStart; !d.isAfter(vacEnd); d = d.plusDays(1)) {
                    // Prüfen, ob der Urlaubstag 'd' in der aktuellen betrachteten Woche liegt
                    if (!d.isBefore(startOfWeek) && !d.isAfter(endOfWeek)) {
                        // Nur "echte" Arbeitstage berücksichtigen (Mo-Fr), oder anpassen, falls Sa/So reguläre Arbeitstage sein können.
                        // isDayOff prüft hier auch auf Feiertage etc., falls implementiert.
                        if (!isDayOff(user, d)) { // isDayOff sollte Sa/So und Feiertage prüfen
                            // Wert eines Urlaubstages für diesen User ermitteln.
                            // Für prozentuale Mitarbeiter ist der "Wert" eines Urlaubstages oft ein Fünftel ihres Wochensolls
                            // (bei einer 5-Tage-Woche Annahme) oder ein Durchschnittswert.
                            // Hier nehmen wir an, ein Urlaubstag ist (Basis-Vollzeit-Tag * Prozentsatz).
                            double dailyFullTimeHoursForVacationCalc = 8.5; // Basis für einen Vollzeittag für Urlaubsbewertung
                            double vacationDayValueHours = dailyFullTimeHoursForVacationCalc * (user.getWorkPercentage() / 100.0);

                            if (vr.isHalfDay()) {
                                vacationMinutesToDeduct += (int) Math.round((vacationDayValueHours / 2.0) * 60);
                            } else {
                                vacationMinutesToDeduct += (int) Math.round(vacationDayValueHours * 60);
                            }
                        }
                    }
                }
            }
        }
        logger.debug("User: {}, Woche ab: {}, Erwartet (Basis): {}min, Abzug Urlaub: {}min, Netto: {}min",
                user.getUsername(), startOfWeek, expectedWeeklyMinutes, vacationMinutesToDeduct, Math.max(0, expectedWeeklyMinutes - vacationMinutesToDeduct));
        return Math.max(0, expectedWeeklyMinutes - vacationMinutesToDeduct);
    }

    public int computeExpectedWorkMinutes(User user, LocalDate date) {
        double expectedHours = getExpectedWorkHours(user, date);

        if (isDayOff(user, date)) return 0;
        if (isHalfDay(user, date)) expectedHours /= 2.0;

        return (int) Math.round(expectedHours * 60);
    }

    public boolean isDayOff(User user, LocalDate date) {
        // Hier deine Logik für freie Tage (Wochenenden, Feiertage, UserScheduleRule)
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return true;
        }

        // Beispielhafte Prüfung auf UserScheduleRule (muss an deine Entität angepasst werden)
        // List<UserScheduleRule> rules = ruleRepo.findByUser(user);
        // for (UserScheduleRule rule : rules) {
        //    // Implementiere hier die Logik, um zu prüfen, ob die Regel den Tag als "OFF" definiert
        //    // z.B. if (matchesRule(rule, date) && "OFF".equals(rule.getDayMode())) return true;
        // }
        return false;
    }

    public boolean isHalfDay(User user, LocalDate date) {
        // Beispielhafte Prüfung auf UserScheduleRule für halbe Tage
        // List<UserScheduleRule> rules = ruleRepo.findByUser(user);
        // for (UserScheduleRule rule : rules) {
        //    // z.B. if (matchesRule(rule, date) && "HALF_DAY".equals(rule.getDayMode())) return true;
        // }
        return false;
    }
}