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

    // Bitte DIESE komplette Methode kopieren und die bestehende Methode ersetzen

    public double getExpectedWorkHours(User user, LocalDate date) {
        // ====================== START DEBUG-CODE ======================
        // Dieser Block schreibt bei jedem Aufruf eine Log-Nachricht.
        logger.info("[CHRONO-DEBUG] getExpectedWorkHours wird aufgerufen für Benutzer: '{}', isHourly: {}, für Datum: {}",
                user.getUsername(), user.getIsHourly(), date.toString());
        // ======================= ENDE DEBUG-CODE =======================

        // Der eigentliche Fix: Prüft, ob der Benutzer auf Stundenbasis arbeitet.
        if (Boolean.TRUE.equals(user.getIsHourly())) {
            logger.info("[CHRONO-DEBUG] Benutzer '{}' ist 'isHourly'. Erwartete Stunden werden auf 0.0 gesetzt.", user.getUsername());
            return 0.0;
        }

        // Unveränderte, bestehende Logik für alle anderen Benutzertypen
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

        // ====================== START DEBUG-CODE ======================
        logger.info("[CHRONO-DEBUG] Für Benutzer: '{}', Datum: {}, berechnete Soll-Stunden: {}", user.getUsername(), date.toString(), baseHours);
        // ======================= ENDE DEBUG-CODE =======================

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


    public int computeExpectedWorkMinutes(User user, LocalDate date, List<VacationRequest> approvedVacationsForUser) {
        // Logik für prozentuale Mitarbeiter (Wochenbasis wird im TimeTrackingService gehandhabt)
        // Hier geben wir den "theoretischen" Tageswert zurück, unter Berücksichtigung von Abwesenheiten,
        // damit TimeTrackingService.computeDailyWorkDifference dies für die Saldenberechnung nutzen kann,
        // auch wenn prozentuale User wöchentlich saldiert werden.
        if (Boolean.TRUE.equals(user.getIsPercentage())) {
            // Prüfe, ob der Tag ein Feiertag ist, der das Soll reduziert (gemäß UserHolidayOption)
            if (isDayOff(user, date)) { // isDayOff berücksichtigt jetzt die Feiertagsoption für %-User
                // Wenn isDayOff true ist (z.B. Feiertag, der Soll reduziert, oder Wochenende) -> Soll ist 0
                return 0;
            }

            // Prüfe auf ganztägigen Urlaub für diesen Tag
            for (VacationRequest vr : approvedVacationsForUser) {
                if (!date.isBefore(vr.getStartDate()) && !date.isAfter(vr.getEndDate())) {
                    if (!vr.isHalfDay()) return 0; // Ganzer Tag Urlaub -> 0 Soll
                }
            }
            // Prüfe auf ganztägige Krankheit
            List<SickLeave> sickLeaves = sickLeaveRepository.findByUser(user);
            for (SickLeave sick : sickLeaves) {
                if (!date.isBefore(sick.getStartDate()) && !date.isAfter(sick.getEndDate())) {
                    if (!sick.isHalfDay()) return 0; // Ganzer Tag krank -> 0 Soll
                }
            }

            // Wenn kein voller freier Tag (Feiertag/Urlaub/Krankheit), berechne den anteiligen Tageswert
            double baseFullTimeWeeklyHours = BASE_FULL_TIME_WEEKLY_HOURS;
            double percentageWeeklyHours = baseFullTimeWeeklyHours * (user.getWorkPercentage() / 100.0);
            Integer expectedWorkDays = user.getExpectedWorkDays() != null && user.getExpectedWorkDays() > 0 ? user.getExpectedWorkDays() : 5;
            double dailyValueHours = percentageWeeklyHours / expectedWorkDays;
            int dailyMinutes = (int) Math.round(dailyValueHours * 60);

            // Halbtägige Abwesenheiten reduzieren dieses Tagessoll
            for (VacationRequest vr : approvedVacationsForUser) {
                if (!date.isBefore(vr.getStartDate()) && !date.isAfter(vr.getEndDate())) {
                    if (vr.isHalfDay()) return dailyMinutes / 2;
                }
            }
            for (SickLeave sick : sickLeaves) {
                if (!date.isBefore(sick.getStartDate()) && !date.isAfter(sick.getEndDate())) {
                    if (sick.isHalfDay()) return dailyMinutes / 2;
                }
            }
            return dailyMinutes; // Normaler anteiliger Arbeitstag für prozentualen User
        }


        // Logik für Standard-User (nicht prozentual)
        // 1. Ist der Tag komplett frei (Feiertag, Wochenende ohne geplante Arbeit)?
        if (isDayOff(user, date)) {
            if ("Chantale".equals(user.getUsername())) {
                logger.info("[User: {}] WorkScheduleService.computeExpectedWorkMinutes (Standard): Datum {}, isDayOff() ist true -> Soll: 0 Min.", user.getUsername(), date);
            }
            return 0;
        }

        // 2. Ganztägiger Urlaub?
        for (VacationRequest vr : approvedVacationsForUser) {
            if (!date.isBefore(vr.getStartDate()) && !date.isAfter(vr.getEndDate())) {
                if (!vr.isHalfDay()) { // Ganzer Tag Urlaub
                    if ("Chantale".equals(user.getUsername())) {
                        logger.info("[User: {}] WorkScheduleService.computeExpectedWorkMinutes (Standard): Datum {}, ganztägiger Urlaub -> Soll: 0 Min.", user.getUsername(), date);
                    }
                    return 0;
                }
            }
        }

        // 3. Ganztägige Krankheit?
        List<SickLeave> sickLeaves = sickLeaveRepository.findByUser(user);
        for (SickLeave sick : sickLeaves) {
            if (!date.isBefore(sick.getStartDate()) && !date.isAfter(sick.getEndDate())) {
                if (!sick.isHalfDay()) { // Ganzer Tag krank
                    if ("Chantale".equals(user.getUsername())) {
                        logger.info("[User: {}] WorkScheduleService.computeExpectedWorkMinutes (Standard): Datum {}, ganztägige Krankheit -> Soll: 0 Min.", user.getUsername(), date);
                    }
                    return 0;
                }
            }
        }

        // 4. Basissoll für den Tag holen (berücksichtigt Wochenplan)
        double expectedHoursFullDay = getExpectedWorkHours(user, date);
        if ("Chantale".equals(user.getUsername())) {
            logger.info("[User: {}] WorkScheduleService.computeExpectedWorkMinutes (Standard): Datum {}, Basissoll (getExpectedWorkHours): {}h", user.getUsername(), date, expectedHoursFullDay);
        }


        // 5. Halbtägige Abwesenheiten prüfen (Urlaub oder Krankheit)
        for (VacationRequest vr : approvedVacationsForUser) {
            if (!date.isBefore(vr.getStartDate()) && !date.isAfter(vr.getEndDate())) {
                if (vr.isHalfDay()) {
                    int halfDaySoll = (int) Math.round((expectedHoursFullDay / 2.0) * 60);
                    if ("Chantale".equals(user.getUsername())) {
                        logger.info("[User: {}] WorkScheduleService.computeExpectedWorkMinutes (Standard): Datum {}, halber Tag Urlaub -> Soll: {} Min.", user.getUsername(), date, halfDaySoll);
                    }
                    return halfDaySoll;
                }
            }
        }
        for (SickLeave sick : sickLeaves) {
            if (!date.isBefore(sick.getStartDate()) && !date.isAfter(sick.getEndDate())) {
                if (sick.isHalfDay()) {
                    int halfDaySoll = (int) Math.round((expectedHoursFullDay / 2.0) * 60);
                    if ("Chantale".equals(user.getUsername())) {
                        logger.info("[User: {}] WorkScheduleService.computeExpectedWorkMinutes (Standard): Datum {}, halber Tag Krankheit -> Soll: {} Min.", user.getUsername(), date, halfDaySoll);
                    }
                    return halfDaySoll;
                }
            }
        }

        // UserScheduleRule für halbe Tage (muss noch implementiert werden, falls benötigt über isHalfDay())
        if (isHalfDay(user, date)) { // Diese Methode ist noch ein TODO
            int halfDaySollFromRule = (int) Math.round((expectedHoursFullDay / 2.0) * 60);
            if ("Chantale".equals(user.getUsername())) {
                logger.info("[User: {}] WorkScheduleService.computeExpectedWorkMinutes (Standard): Datum {}, halber Tag per UserScheduleRule -> Soll: {} Min.", user.getUsername(), date, halfDaySollFromRule);
            }
            return halfDaySollFromRule;
        }


        // 6. Wenn keine der obigen Bedingungen zutrifft, volles Tagessoll
        int finalSoll = (int) Math.round(expectedHoursFullDay * 60);
        if ("Chantale".equals(user.getUsername())) {
            logger.info("[User: {}] WorkScheduleService.computeExpectedWorkMinutes (Standard): Datum {}, reguläres volles Soll -> {} Min.", user.getUsername(), date, finalSoll);
        }
        return finalSoll;
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
        List<UserScheduleRule> rules = ruleRepo.findByUser(user);
        for (UserScheduleRule rule : rules) {
            if (rule.getDayMode() == null || !"HALF_DAY".equalsIgnoreCase(rule.getDayMode())) {
                continue;
            }

            LocalDate start = rule.getStartDate();
            if (start != null && date.isBefore(start)) {
                continue;
            }

            if (rule.getDayOfWeek() != null && rule.getDayOfWeek() != date.getDayOfWeek().getValue()) {
                continue;
            }

            if (rule.getRepeatIntervalDays() != null && rule.getRepeatIntervalDays() > 0 && start != null) {
                long diff = ChronoUnit.DAYS.between(start, date);
                if (diff % rule.getRepeatIntervalDays() == 0) {
                    return true;
                }
            } else {
                // Regel gilt einmalig oder jede Woche bei passendem dayOfWeek
                return true;
            }
        }
        return false;
    }
}