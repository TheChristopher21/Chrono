package com.chrono.chrono.services;

import com.chrono.chrono.entities.SickLeave; // Importiert
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserHolidayOption; // NEU
import com.chrono.chrono.entities.UserScheduleRule;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.repositories.SickLeaveRepository; // Importiert
import com.chrono.chrono.repositories.UserHolidayOptionRepository; // NEU
import com.chrono.chrono.repositories.UserScheduleRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional; // NEU

@Service
public class WorkScheduleService {

    public static final Logger logger = LoggerFactory.getLogger(WorkScheduleService.class);

    @Autowired
    private UserScheduleRuleRepository ruleRepo;

    @Autowired
    private HolidayService holidayService;

    @Autowired
    private SickLeaveRepository sickLeaveRepository;

    @Autowired // NEU
    private UserHolidayOptionRepository userHolidayOptionRepository;

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
                        logger.warn("Cycle index {} for user {} on date {} is out of bounds for schedule size {}. Using fallback to dailyWorkHours or default.",
                                cycleIndex, user.getUsername(), date, schedule.size());
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
            logger.warn("getExpectedWeeklyMinutesForPercentageUser called for non-percentage user {} or user with no workPercentage.", user.getUsername());
            return 0;
        }

        double baseFullTimeWeeklyHours = BASE_FULL_TIME_WEEKLY_HOURS;
        double percentageWeeklyHours = baseFullTimeWeeklyHours * (user.getWorkPercentage() / 100.0);
        int expectedWeeklyMinutesTotal = (int) Math.round(percentageWeeklyHours * 60);

        int absenceMinutesToDeduct = 0;
        LocalDate startOfWeek = dateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        List<SickLeave> sickLeavesForUser = sickLeaveRepository.findByUser(user);
        // NEU: Lade die Feiertagsoptionen für die Woche
        List<UserHolidayOption> holidayOptionsInWeek = userHolidayOptionRepository.findByUserAndHolidayDateBetween(user, startOfWeek, endOfWeek);

        Integer expectedWorkDays = user.getExpectedWorkDays();
        double dailyValueHours;

        if (expectedWorkDays != null && expectedWorkDays >= 1 && expectedWorkDays <= 7) {
            dailyValueHours = percentageWeeklyHours / expectedWorkDays;
        } else {
            dailyValueHours = percentageWeeklyHours / 5.0; // Fallback auf 5-Tage-Woche
            logger.warn("User {} (Percentage: {}%) has invalid or no expectedWorkDays ({}). Using 5-day week for absence calculation within week {}.",
                    user.getUsername(), user.getWorkPercentage(), expectedWorkDays, startOfWeek);
        }
        int dailyValueMinutes = (int) Math.round(dailyValueHours * 60);

        for (LocalDate d = startOfWeek; !d.isAfter(endOfWeek); d = d.plusDays(1)) {
            final LocalDate currentDate = d;
            DayOfWeek day = d.getDayOfWeek();

            // Wochenenden grundsätzlich ignorieren, es sei denn, der User hat explizit Arbeitstage am Wochenende
            // in seinem `expectedWorkDays` Modell (was hier vereinfacht als Mo-Fr oder Mo-So angenommen wird).
            // Eine präzisere Logik bräuchte die exakten Arbeitstage des Users.
            // Für diese Implementierung: Wenn `expectedWorkDays` > 5, werden Sa/So potenziell als Arbeitstage gewertet.
            boolean isPotentialWorkDayBasedOnModel = true;
            if (expectedWorkDays <= 5 && (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)) {
                isPotentialWorkDayBasedOnModel = false;
            }
            if (expectedWorkDays == 6 && day == DayOfWeek.SUNDAY) { // Bei 6-Tage-Modell ist Sonntag frei
                isPotentialWorkDayBasedOnModel = false;
            }
            // Bei 7-Tage-Modell sind alle Tage potenzielle Arbeitstage

            if (!isPotentialWorkDayBasedOnModel) continue;


            String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;
            boolean isActualHoliday = holidayService.isHoliday(d, cantonAbbreviation);

            // Abwesenheiten (Urlaub, Krankheit)
            boolean vacationToday = approvedVacationsInWeek.stream()
                    .anyMatch(vr -> vr.isApproved() && !currentDate.isBefore(vr.getStartDate()) && !currentDate.isAfter(vr.getEndDate()));
            boolean sickToday = sickLeavesForUser.stream()
                    .anyMatch(sl -> !currentDate.isBefore(sl.getStartDate()) && !currentDate.isAfter(sl.getEndDate()));

            VacationRequest relevantVacation = approvedVacationsInWeek.stream()
                    .filter(vr -> vr.isApproved() && !currentDate.isBefore(vr.getStartDate()) && !currentDate.isAfter(vr.getEndDate()))
                    .findFirst().orElse(null);
            SickLeave relevantSickLeave = sickLeavesForUser.stream()
                    .filter(sl -> !currentDate.isBefore(sl.getStartDate()) && !currentDate.isAfter(sl.getEndDate()))
                    .findFirst().orElse(null);


            if (isActualHoliday) {
                Optional<UserHolidayOption> holidayOptionOpt = holidayOptionsInWeek.stream()
                        .filter(ho -> ho.getHolidayDate().equals(currentDate))
                        .findFirst();

                UserHolidayOption.HolidayHandlingOption handling = holidayOptionOpt
                        .map(UserHolidayOption::getHolidayHandlingOption)
                        .orElse(UserHolidayOption.HolidayHandlingOption.PENDING_DECISION); // Default, falls keine explizite Option

                if (handling == UserHolidayOption.HolidayHandlingOption.DEDUCT_FROM_WEEKLY_TARGET) {
                    absenceMinutesToDeduct += dailyValueMinutes; // Feiertag reduziert Soll
                } else if (handling == UserHolidayOption.HolidayHandlingOption.DO_NOT_DEDUCT_FROM_WEEKLY_TARGET) {
                    // Feiertag reduziert das Soll NICHT, also keine Änderung an absenceMinutesToDeduct
                } else { // PENDING_DECISION - hier Standard: Soll NICHT reduzieren, bis Admin entscheidet
                    // Keine Reduktion, da Admin noch entscheiden muss. Arbeit an diesem Tag wäre regulär.
                }
            } else if (relevantVacation != null) { // Wenn kein Feiertag, aber Urlaub
                if (relevantVacation.isHalfDay()) {
                    absenceMinutesToDeduct += dailyValueMinutes / 2;
                } else {
                    absenceMinutesToDeduct += dailyValueMinutes;
                }
            } else if (relevantSickLeave != null) { // Wenn kein Feiertag oder Urlaub, aber krank
                if (relevantSickLeave.isHalfDay()) {
                    absenceMinutesToDeduct += dailyValueMinutes / 2;
                } else {
                    absenceMinutesToDeduct += dailyValueMinutes;
                }
            }
        }

        int netExpectedMinutes = Math.max(0, expectedWeeklyMinutesTotal - absenceMinutesToDeduct);
        logger.debug("User: {}, Week starting: {}, Base Expected ({}%): {}min, Total Absence Deduction (Vacation+Sick+Holiday): {}min, Net Expected: {}min",
                user.getUsername(), startOfWeek, user.getWorkPercentage(),expectedWeeklyMinutesTotal, absenceMinutesToDeduct, netExpectedMinutes);
        return netExpectedMinutes;
    }


    public int computeExpectedWorkMinutes(User user, LocalDate date) {
        // Für prozentuale User wird das Tagessoll nicht direkt hier berechnet, sondern über das Wochensoll.
        // Diese Methode wird primär für Standard-User und für die Anzeige im TimeTrackingService relevant sein.
        if (Boolean.TRUE.equals(user.getIsPercentage())) {
            // Für die Anzeige eines *einzelnen* Tages für einen prozentualen User:
            // Man könnte hier den "Wert" eines Tages basierend auf dem Wochensoll / Arbeitstage anzeigen,
            // aber die Saldo-Berechnung erfolgt wochenbasiert.
            // Hier geben wir 0 zurück, da das Saldo anders berechnet wird.
            // Alternativ könnte man hier auch den anteiligen Tageswert zurückgeben, wenn isDayOff false ist.
            // Wir müssen aber konsistent mit getExpectedWeeklyMinutesForPercentageUser sein.
            // Da getExpectedWeeklyMinutesForPercentageUser die Feiertagsoptionen berücksichtigt, tun wir das hier auch.

            if (isDayOff(user, date)) { // Generelle Prüfung auf Wochenende/Feiertag
                String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;
                boolean isActualHoliday = holidayService.isHoliday(date, cantonAbbreviation);
                if(isActualHoliday) {
                    Optional<UserHolidayOption> holidayOptionOpt = userHolidayOptionRepository.findByUserAndHolidayDate(user, date);
                    UserHolidayOption.HolidayHandlingOption handling = holidayOptionOpt
                            .map(UserHolidayOption::getHolidayHandlingOption)
                            .orElse(UserHolidayOption.HolidayHandlingOption.PENDING_DECISION);
                    if (handling == UserHolidayOption.HolidayHandlingOption.DEDUCT_FROM_WEEKLY_TARGET) {
                        return 0; // Soll wird an diesem Tag durch Feiertag reduziert
                    } else {
                        // DO_NOT_DEDUCT oder PENDING: Soll wird NICHT reduziert, als ob es ein Arbeitstag wäre
                        // Berechne den "normalen" Wert dieses Tages für den prozentualen Mitarbeiter
                        double baseFullTimeWeeklyHours = BASE_FULL_TIME_WEEKLY_HOURS;
                        double percentageWeeklyHours = baseFullTimeWeeklyHours * (user.getWorkPercentage() / 100.0);
                        Integer expectedWorkDays = user.getExpectedWorkDays() != null && user.getExpectedWorkDays() > 0 ? user.getExpectedWorkDays() : 5;
                        double dailyValueHours = percentageWeeklyHours / expectedWorkDays;
                        return (int) Math.round(dailyValueHours * 60);
                    }
                }
                return 0; // An normalen Wochenenden ist das Soll 0.
            }
            // Wenn kein Feiertag/Wochenende, dann den normalen Tageswert für prozentuale MA berechnen
            double baseFullTimeWeeklyHours = BASE_FULL_TIME_WEEKLY_HOURS;
            double percentageWeeklyHours = baseFullTimeWeeklyHours * (user.getWorkPercentage() / 100.0);
            Integer expectedWorkDays = user.getExpectedWorkDays() != null && user.getExpectedWorkDays() > 0 ? user.getExpectedWorkDays() : 5;
            double dailyValueHours = percentageWeeklyHours / expectedWorkDays;
            int dailyMinutes = (int) Math.round(dailyValueHours * 60);

            // Krankheit prüfen
            List<SickLeave> sickLeaves = sickLeaveRepository.findByUser(user);
            for (SickLeave sick : sickLeaves) {
                if (!date.isBefore(sick.getStartDate()) && !date.isAfter(sick.getEndDate())) {
                    return sick.isHalfDay() ? dailyMinutes / 2 : 0;
                }
            }
            return dailyMinutes; // Normaler Arbeitstag für prozentualen User
        }

        // Für Standard-User (nicht prozentual)
        if (isDayOff(user, date)) { // Berücksichtigt Feiertage und Wochenenden
            return 0;
        }

        List<SickLeave> sickLeaves = sickLeaveRepository.findByUser(user);
        for (SickLeave sick : sickLeaves) {
            if (!date.isBefore(sick.getStartDate()) && !date.isAfter(sick.getEndDate())) {
                if (sick.isHalfDay()) {
                    double expectedHoursFullDay = getExpectedWorkHours(user, date);
                    return (int) Math.round((expectedHoursFullDay / 2.0) * 60);
                } else {
                    return 0;
                }
            }
        }

        double expectedHours = getExpectedWorkHours(user, date);

        // UserScheduleRule für halbe Tage (muss noch implementiert werden, falls benötigt)
        // if (isHalfDay(user, date)) {
        //    expectedHours /= 2.0;
        // }

        return (int) Math.round(expectedHours * 60);
    }

    public boolean isDayOff(User user, LocalDate date) {
        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;

        if (holidayService.isHoliday(date, cantonAbbreviation)) {
            if (Boolean.TRUE.equals(user.getIsPercentage())) {
                Optional<UserHolidayOption> holidayOptionOpt = userHolidayOptionRepository.findByUserAndHolidayDate(user, date);
                UserHolidayOption.HolidayHandlingOption handling = holidayOptionOpt
                        .map(UserHolidayOption::getHolidayHandlingOption)
                        .orElse(UserHolidayOption.HolidayHandlingOption.PENDING_DECISION);
                if (handling == UserHolidayOption.HolidayHandlingOption.DEDUCT_FROM_WEEKLY_TARGET) {
                    logger.debug("Date {} is a holiday and DEDUCTS from target for percentage user {}. isDayOff returns true.", date, user.getUsername());
                    return true;
                } else {
                    logger.debug("Date {} is a holiday but DOES NOT DEDUCT (or PENDING) from target for percentage user {}. isDayOff returns false for soll-calculation.", date, user.getUsername());
                    return false;
                }
            } else {
                logger.debug("Date {} is a holiday for non-percentage user {}. isDayOff returns true.", date, user.getUsername());
                return true;
            }
        }

        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            if (!Boolean.TRUE.equals(user.getIsPercentage())) {
                double expectedHoursOnWeekend = getExpectedWorkHours(user, date);
                if (expectedHoursOnWeekend > 0) {
                    logger.trace("Date {} is a weekend, but user {} has scheduled hours. isDayOff returns false.", date, user.getUsername());
                    return false;
                }
            }
            logger.debug("Date {} is a weekend day for user {}. isDayOff returns true (or handled by percentage logic).", date, user.getUsername());
            return true;
        }

        logger.trace("Date {} is not a holiday or weekend for user {}. isDayOff returns false.", date, user.getUsername());
        return false;
    }


    public boolean isHalfDay(User user, LocalDate date) {
        // TODO: UserScheduleRule prüfen, ob dieser Tag spezifisch als "HALF_DAY" markiert ist.
        // Diese Methode ist derzeit nicht voll implementiert und liefert immer false.
        // Sie wird in `computeExpectedWorkMinutes` verwendet, aber hat aktuell keinen Effekt.
        return false;
    }
}