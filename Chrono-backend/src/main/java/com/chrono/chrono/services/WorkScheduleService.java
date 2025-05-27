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
    private UserScheduleRuleRepository ruleRepo; // ruleRepo is not used yet, but kept for future schedule rule logic

    // Basis Vollzeit Wochenstunden für 100% - aus einer Konfiguration oder als Konstante.
    // Dies repräsentiert die Standard-Wochenarbeitszeit einer 100%-Stelle im Unternehmen.
    private static final double BASE_FULL_TIME_WEEKLY_HOURS = 42.5;


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
            if (schedule != null && user.getScheduleCycle() != null && !schedule.isEmpty() && user.getScheduleCycle() > 0) {
                try {
                    int cycleLength = user.getScheduleCycle();
                    LocalDate epochMonday = LocalDate.of(2020, 1, 6);
                    LocalDate startOfWeekForDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    long weeksSinceEpoch = ChronoUnit.WEEKS.between(epochMonday, startOfWeekForDate);

                    int cycleIndex = (int) (weeksSinceEpoch % cycleLength);

                    if (cycleIndex < 0) {
                        cycleIndex += cycleLength;
                    }

                    if (cycleIndex >= schedule.size()) {
                        logger.warn("Cycle index {} for user {} is out of bounds for schedule size {}. Using fallback. Date: {}",
                                cycleIndex, user.getUsername(), schedule.size(), date);
                        baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
                    } else {
                        Map<String, Double> weekSchedule = schedule.get(cycleIndex);
                        String dayOfWeekKey = date.getDayOfWeek().name().toLowerCase();
                        baseHours = weekSchedule.getOrDefault(dayOfWeekKey, 0.0);
                    }
                } catch (Exception ex) {
                    logger.error("Error processing weekly schedule for user {}: {}. Falling back to dailyWorkHours or default.", user.getUsername(), ex.getMessage(), ex);
                    baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
                }
            } else {
                baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
            }
        }
        return applyPercentage(user, baseHours);
    }

    public int getExpectedWeeklyMinutesForPercentageUser(User user, LocalDate dateInWeek, List<VacationRequest> approvedVacationsInWeek) {
        if (!Boolean.TRUE.equals(user.getIsPercentage()) || user.getWorkPercentage() == null) {
            // Diese Methode ist nur für prozentuale Mitarbeiter mit definiertem Prozentsatz gedacht.
            // Für andere Typen oder fehlenden Prozentsatz wird 0 zurückgegeben oder ein Fehler geworfen.
            logger.warn("getExpectedWeeklyMinutesForPercentageUser called for non-percentage user {} or user with no workPercentage.", user.getUsername());
            return 0;
        }

        // Basis-Wochenstunden für 100%
        double baseFullTimeWeeklyHours = BASE_FULL_TIME_WEEKLY_HOURS; // Using the defined constant

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
                        // isDayOff prüft hier auch auf Feiertage etc., falls implementiert.
                        if (!isDayOff(user, d)) { // isDayOff sollte Sa/So und Feiertage prüfen
                            double vacationDayValueHours;
                            // MODIFIED: Check Integer value and range for expectedWorkDays
                            if (user.getExpectedWorkDays() != null && user.getExpectedWorkDays() >= 1 && user.getExpectedWorkDays() <= 7) {
                                vacationDayValueHours = percentageWeeklyHours / user.getExpectedWorkDays();
                                logger.debug("User {}: Calculating vacation day value based on expectedWorkDays {}: {} hours, for week starting: {}", user.getUsername(), user.getExpectedWorkDays(), vacationDayValueHours, startOfWeek);
                            } else {
                                vacationDayValueHours = percentageWeeklyHours / 5.0; // Standardmäßig 5 Tage, wenn nicht anders angegeben
                                logger.warn("User {} is percentage based but has invalid or no expectedWorkDays set (Value: {}). Calculated vacationDayValueHours (Fallback): {} hours, for week starting: {}", user.getUsername(), user.getExpectedWorkDays(), vacationDayValueHours, startOfWeek);
                            }

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
        logger.debug("User: {}, Week starting: {}, Expected (Base): {}min, Vacation Deduction: {}min, Net Expected: {}min",
                user.getUsername(), startOfWeek, expectedWeeklyMinutes, vacationMinutesToDeduct, Math.max(0, expectedWeeklyMinutes - vacationMinutesToDeduct));
        return Math.max(0, expectedWeeklyMinutes - vacationMinutesToDeduct);
    }

    public int computeExpectedWorkMinutes(User user, LocalDate date) {
        double expectedHours = getExpectedWorkHours(user, date);

        // TODO: Hier die Logik für Feiertage und individuelle freie Tage (UserScheduleRule) verfeinern.
        // Aktuell prüft isDayOff nur Sa/So. isHalfDay ist noch nicht implementiert.
        if (isDayOff(user, date)) return 0;
        if (isHalfDay(user, date)) expectedHours /= 2.0;

        return (int) Math.round(expectedHours * 60);
    }

    // Prüft auf Wochenende (vereinfacht, ohne Feiertage oder UserScheduleRules)
    public boolean isDayOff(User user, LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            // Hier könnte man noch prüfen, ob Sa/So laut Wochenplan des Users Arbeitstage sind
            // Für die meisten Standardfälle sind sie frei.
            // List<Map<String, Double>> schedule = user.getWeeklySchedule();
            // if (schedule != null && !schedule.isEmpty() && user.getScheduleCycle() != null && user.getScheduleCycle() > 0) {
            //     // ... komplexe Logik zur Bestimmung, ob der Tag im aktuellen Zyklus > 0 Stunden hat ...
            //     // Wenn ja, dann ist es KEIN freier Tag, auch wenn es Sa/So ist.
            // }
            return true; // Vereinfachte Annahme: Sa/So sind immer frei
        }
        // TODO: Feiertage prüfen
        // TODO: UserScheduleRule prüfen (z.B. "EVERY_2_WEEKS_FRIDAY_OFF")
        return false;
    }

    // Noch nicht implementiert, Platzhalter
    public boolean isHalfDay(User user, LocalDate date) {
        // TODO: UserScheduleRule prüfen (z.B. für "HALF_DAY")
        return false;
    }
}