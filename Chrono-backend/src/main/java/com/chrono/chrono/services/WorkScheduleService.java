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
        // Berücksichtigung des scheduleEffectiveDate
        if (user.getScheduleEffectiveDate() != null && date.isBefore(user.getScheduleEffectiveDate())) {
            // Fallback, wenn Datum VOR dem Gültigkeitsdatum des Wochenplans liegt
            baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
            // Logging für Chantale (wie von dir gewünscht, hier eingefügt)
            if ("Chantale".equals(user.getUsername())) {
                logger.debug("[User: Chantale] Backend getExpectedWorkHours: Datum {} ist vor scheduleEffectiveDate {}. Fallback auf user.getDailyWorkHours() ({}) oder 8.5. Resultierende baseHours: {}", date, user.getScheduleEffectiveDate(), user.getDailyWorkHours(), baseHours);
            }
        } else {
            // Logik für Wochenplan anwenden
            List<Map<String, Double>> schedule = user.getWeeklySchedule();
            if (schedule != null && user.getScheduleCycle() != null && !schedule.isEmpty() && user.getScheduleCycle() > 0) {
                if ("Chantale".equals(user.getUsername())) {
                    logger.debug("[User: Chantale] Backend getExpectedWorkHours: Wochenplan wird angewendet für Datum {}. scheduleCycle: {}, weeklySchedule Länge: {}, weeklySchedule Inhalt: {}", date, user.getScheduleCycle(), schedule.size(), schedule);
                }
                try {
                    int cycleLength = user.getScheduleCycle();
                    LocalDate epochMonday = LocalDate.of(2020, 1, 6);
                    LocalDate startOfWeekForDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    long weeksSinceEpoch = ChronoUnit.WEEKS.between(epochMonday, startOfWeekForDate);
                    int cycleIndex = (int) (weeksSinceEpoch % cycleLength);
                    if (cycleIndex < 0) {
                        cycleIndex += cycleLength;
                    }

                    String dayOfWeekKey = date.getDayOfWeek().name().toLowerCase();
                    if ("Chantale".equals(user.getUsername())) {
                        logger.debug("[User: Chantale] Backend getExpectedWorkHours: Für Datum {} (Wochentag: {}): weeksSinceEpoch={}, cycleIndex={}", date, dayOfWeekKey, weeksSinceEpoch, cycleIndex);
                    }

                    if (cycleIndex >= schedule.size()) {
                        // Fallback, wenn weeklySchedule zu kurz für den cycleIndex ist
                        logger.warn("Cycle index {} for user {} on date {} is out of bounds for schedule size {}. Using fallback to dailyWorkHours or default.",
                                cycleIndex, user.getUsername(), date, schedule.size());
                        baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
                        if ("Chantale".equals(user.getUsername())) {
                            logger.debug("[User: Chantale] Backend getExpectedWorkHours: cycleIndex {} >= schedule.size() {}. Fallback auf user.getDailyWorkHours() ({}) oder 8.5. Resultierende baseHours: {}", cycleIndex, schedule.size(), user.getDailyWorkHours(), baseHours);
                        }
                    } else {
                        Map<String, Double> weekScheduleMap = schedule.get(cycleIndex);
                        if ("Chantale".equals(user.getUsername())) {
                            logger.debug("[User: Chantale] Backend getExpectedWorkHours: weekScheduleMap für cycleIndex {}: {}", cycleIndex, weekScheduleMap);
                        }
                        baseHours = weekScheduleMap.getOrDefault(dayOfWeekKey, 0.0); // Fallback auf 0.0 wenn Tag nicht in Map
                        if ("Chantale".equals(user.getUsername())) {
                            logger.debug("[User: Chantale] Backend getExpectedWorkHours: Stunden aus weeklyScheduleMap für {}: {}", dayOfWeekKey, baseHours);
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error processing weekly schedule for user {}: {}. Falling back to dailyWorkHours or default.", user.getUsername(), ex.getMessage(), ex);
                    baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
                    if ("Chantale".equals(user.getUsername())) {
                        logger.error("[User: Chantale] Backend getExpectedWorkHours: Fehler in Wochenplanlogik für Datum {}. Fallback auf user.getDailyWorkHours() ({}) oder 8.5. Resultierende baseHours: {}", date, user.getDailyWorkHours(), baseHours, ex);
                    }
                }
            } else {
                // Kein gültiger Wochenplan definiert, Fallback
                baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
                if ("Chantale".equals(user.getUsername())) {
                    logger.debug("[User: Chantale] Backend getExpectedWorkHours: Kein gültiger Wochenplan für Datum {}. Fallback auf user.getDailyWorkHours() ({}) oder 8.5. Resultierende baseHours: {}", date, user.getDailyWorkHours(), baseHours);
                }
            }
        }
        // applyPercentage tut hier für Standard-Nutzer nichts und gibt baseHours unverändert zurück.
        double finalHours = applyPercentage(user, baseHours);
        if ("Chantale".equals(user.getUsername())) {
            logger.debug("[User: Chantale] Backend getExpectedWorkHours: Ergebnis für Datum {}: {}h (nach applyPercentage)", date, finalHours);
        }
        return finalHours;
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
        // Für prozentuale User (Logik wie besprochen, die approvedVacationsForUser nutzt)
        if (Boolean.TRUE.equals(user.getIsPercentage())) {
            // ... (gekürzte Darstellung, die Logik für Urlaub/Krankheit ist hier wichtig)
            boolean isFullDayVacationPercentage = approvedVacationsForUser.stream()
                    .anyMatch(vr -> !vr.isHalfDay() && !date.isBefore(vr.getStartDate()) && !date.isAfter(vr.getEndDate()));
            List<SickLeave> sickLeavesPercentage = sickLeaveRepository.findByUser(user);
            boolean isFullDaySickPercentage = sickLeavesPercentage.stream()
                    .anyMatch(sl -> !sl.isHalfDay() && !date.isBefore(sl.getStartDate()) && !date.isAfter(sl.getEndDate()));

            if (isFullDayVacationPercentage || isFullDaySickPercentage) {
                return 0;
            }
            // ... (restliche Logik für prozentuale User, die dailyMinutesBasePercentage berechnet und ggf. halbiert) ...
            // Beispiel:
            int dailyMinutesBasePercentage = 0;
            if (!isDayOff(user, date)) { /* ... berechne Basissoll ... */ }
            // ... (Logik für halbtägige Abwesenheiten) ...
            return dailyMinutesBasePercentage;
        }

        // Für Standard-User (nicht prozentual, nicht stündlich)
        boolean isFullDayVacationStandard = approvedVacationsForUser.stream()
                .anyMatch(vr -> !vr.isHalfDay() && !date.isBefore(vr.getStartDate()) && !date.isAfter(vr.getEndDate()));
        if (isFullDayVacationStandard) {
            // Logging hier ist wichtig, um zu sehen, ob dieser Zweig erreicht wird!
            if ("Chantale".equals(user.getUsername())) { // Oder allgemeines Logging
                logger.info("[User: {}] computeExpectedWorkMinutes (Standard): Datum {}, ganztägiger Urlaub -> Soll: 0 Min.", user.getUsername(), date);
            }
            return 0;
        }

        List<SickLeave> sickLeavesStandard = sickLeaveRepository.findByUser(user);
        boolean isFullDaySickStandard = sickLeavesStandard.stream()
                .anyMatch(sl -> !sl.isHalfDay() && !date.isBefore(sl.getStartDate()) && !date.isAfter(sl.getEndDate()));
        if (isFullDaySickStandard) {
            if ("Chantale".equals(user.getUsername())) {
                logger.info("[User: {}] computeExpectedWorkMinutes (Standard): Datum {}, ganztägige Krankheit -> Soll: 0 Min.", user.getUsername(), date);
            }
            return 0;
        }

        if (isDayOff(user, date)) {
            if ("Chantale".equals(user.getUsername())) {
                logger.info("[User: {}] computeExpectedWorkMinutes (Standard): Datum {}, isDayOff() ist true -> Soll: 0 Min. (Feiertag/freies WE)", user.getUsername(), date);
            }
            return 0;
        }

        double expectedHoursFullDay = getExpectedWorkHours(user, date);
        if ("Chantale".equals(user.getUsername())) {
            logger.info("[User: {}] computeExpectedWorkMinutes (Standard): Datum {}, Basissoll (getExpectedWorkHours): {}h", user.getUsername(), date, expectedHoursFullDay);
        }

        boolean isHalfDaySickStandard = sickLeavesStandard.stream()
                .anyMatch(sl -> sl.isHalfDay() && !date.isBefore(sl.getStartDate()) && !date.isAfter(sl.getEndDate()));
        if (isHalfDaySickStandard) {
            int halfDaySoll = (int) Math.round((expectedHoursFullDay / 2.0) * 60);
            if ("Chantale".equals(user.getUsername())) {
                logger.info("[User: {}] computeExpectedWorkMinutes (Standard): Datum {}, halber Tag Krankheit -> Soll: {} Min.", user.getUsername(), date, halfDaySoll);
            }
            return halfDaySoll;
        }

        boolean isHalfDayVacationStandard = approvedVacationsForUser.stream()
                .anyMatch(vr -> vr.isHalfDay() && vr.getStartDate().equals(date) && vr.getEndDate().equals(date));
        if (isHalfDayVacationStandard) {
            int halfDaySoll = (int) Math.round((expectedHoursFullDay / 2.0) * 60);
            if ("Chantale".equals(user.getUsername())) {
                logger.info("[User: {}] computeExpectedWorkMinutes (Standard): Datum {}, halber Tag Urlaub -> Soll: {} Min.", user.getUsername(), date, halfDaySoll);
            }
            return halfDaySoll;
        }

        int finalSoll = (int) Math.round(expectedHoursFullDay * 60);
        if ("Chantale".equals(user.getUsername())) {
            logger.info("[User: {}] computeExpectedWorkMinutes (Standard): Datum {}, reguläres volles Soll -> {} Min.", user.getUsername(), date, finalSoll);
        }
        return finalSoll;
    }

    public boolean isDayOff(User user, LocalDate date) {
        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;

        // Für prozentuale Mitarbeiter: Feiertagsbehandlung hängt von UserHolidayOption ab.
        // Diese Methode soll nur return true, wenn es *definitiv* ein freier Tag ist (Wochenende oder Feiertag, der immer frei ist).
        // Die Reduktion des Solls für prozentuale MA an Feiertagen wird in getExpectedWeeklyMinutesForPercentageUser und computeExpectedWorkMinutes gehandhabt.
        if (Boolean.TRUE.equals(user.getIsPercentage())) {
            if (holidayService.isHoliday(date, cantonAbbreviation)) {
                // Prüfung, ob der Feiertag das Soll reduziert oder nicht, erfolgt in den Soll-Berechnungsmethoden
                // Hier geben wir für die reine "isDayOff"-Prüfung false zurück, wenn der Feiertag das Soll NICHT reduziert,
                // da es dann wie ein Arbeitstag behandelt wird.
                Optional<UserHolidayOption> holidayOptionOpt = userHolidayOptionRepository.findByUserAndHolidayDate(user, date);
                UserHolidayOption.HolidayHandlingOption handling = holidayOptionOpt
                        .map(UserHolidayOption::getHolidayHandlingOption)
                        .orElse(UserHolidayOption.HolidayHandlingOption.PENDING_DECISION);
                if (handling == UserHolidayOption.HolidayHandlingOption.DO_NOT_DEDUCT_FROM_WEEKLY_TARGET || handling == UserHolidayOption.HolidayHandlingOption.PENDING_DECISION) {
                    // Wenn das Soll nicht reduziert wird (oder Entscheidung ausstehend), ist es für die Saldo-Logik kein "freier Tag" in dem Sinne, dass das Soll wegfällt.
                    // Es ist aber trotzdem ein Feiertag für die Anzeige.
                    // Für isDayOff() in der Soll-Logik:
                    // return false; // Damit das Tagessoll in computeExpectedWorkMinutes berechnet wird.
                } else { // DEDUCT_FROM_WEEKLY_TARGET
                    // return true; // Ist ein freier Tag, Soll wird reduziert.
                }
                // Für die allgemeine Logik von isDayOff (z.B. Anzeige im Kalender) lassen wir es als Feiertag gelten:
                // logger.debug("Date {} is a holiday for percentage user {} (Canton: {}). isDayOff based on option.", date, user.getUsername(), cantonAbbreviation);
                // Hier müssen wir uns entscheiden: Soll `isDayOff` für prozentuale MA an Feiertagen immer true liefern,
                // und die Logik der Soll-Reduktion passiert woanders? Oder soll `isDayOff` die Option berücksichtigen?
                // Für die Soll-Berechnung ist es besser, wenn isDayOff die Option NICHT direkt berücksichtigt, sondern die Soll-Methoden das tun.
                // Für reine Anzeige "Ist es ein Feiertag?" -> holidayService.isHoliday()
                // Für "Ist es ein Tag, an dem *garantiert* kein Soll anfällt?"
                // Die aktuelle Version von isDayOff in computeExpectedWorkMinutes für percentage user tut dies.
                // Für die allgemeine Definition, lassen wir es hier erstmal so:
                if (holidayService.isHoliday(date, cantonAbbreviation)) return true;
            }
        } else { // Für nicht-prozentuale Mitarbeiter
            if (holidayService.isHoliday(date, cantonAbbreviation)) {
                logger.debug("Date {} is a holiday for user {} (Canton: {}). isDayOff returns true.", date, user.getUsername(), cantonAbbreviation);
                return true;
            }
        }

        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            // Für Standard-User: Prüfen, ob am Wochenende ein spezifisches Soll laut Plan besteht
            if (!Boolean.TRUE.equals(user.getIsPercentage())) {
                double expectedHoursOnWeekend = getExpectedWorkHours(user, date); // Diese Methode berücksichtigt den Wochenplan
                if (expectedHoursOnWeekend > 0) {
                    logger.trace("Date {} is a weekend, but user {} has scheduled hours. isDayOff returns false.", date, user.getUsername());
                    return false; // Es ist ein Wochenende, aber es gibt geplante Stunden, also kein "freier Tag" im Sinne von 0 Soll.
                }
            }
            logger.debug("Date {} is a weekend day. isDayOff returns true.", date);
            return true; // Generell Wochenende frei, wenn nicht explizit Arbeitsstunden geplant (für Standard MA) oder prozentualer MA (dessen WE-Logik oben ist)
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