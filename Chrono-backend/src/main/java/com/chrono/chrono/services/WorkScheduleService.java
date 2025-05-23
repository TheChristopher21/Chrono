package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserScheduleRule;
import com.chrono.chrono.repositories.UserScheduleRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class WorkScheduleService {

    @Autowired
    private UserScheduleRuleRepository ruleRepo;
    // ⬇︎ Füge das irgendwo in der Klasse hinzu – z. B. direkt unter den @Autowired-Zeilen

    private double applyPercentage(User user, double hours) {
        if (Boolean.TRUE.equals(user.getIsPercentage())
                && user.getWorkPercentage() != null) {
            return hours * user.getWorkPercentage() / 100.0;
        }
        return hours;
    }


    public double getExpectedWorkHours(User user, LocalDate date) {

        // 1) Grund-Sollzeit für Vollzeit ermitteln
        double baseHours;
        if (user.getScheduleEffectiveDate() != null && date.isBefore(user.getScheduleEffectiveDate())) {
            baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
        } else {
            List<Map<String, Double>> schedule = user.getWeeklySchedule();
            if (schedule != null && user.getScheduleCycle() != null) {
                try {
                    int cycleLength   = user.getScheduleCycle();
                    LocalDate epoch   = LocalDate.of(2020, 1, 1);
                    long diffWeeks    = ChronoUnit.WEEKS.between(epoch, date);
                    int index         = (int) (diffWeeks % cycleLength);
                    Map<String, Double> weekSchedule = schedule.get(index);
                    String dayOfWeek  = date.getDayOfWeek().toString().toLowerCase();
                    baseHours = weekSchedule.getOrDefault(
                            dayOfWeek,
                            (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5);
                } catch (Exception ex) {
                    // Fallback, wenn Wochenplan nicht greift
                    baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
                }
            } else {
                baseHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
            }
        }

        // 2) EINMALIGE Prozent-Anpassung
        return applyPercentage(user, baseHours);
    }


    public boolean isDayOff(User user, LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;   // Wochenenden frei
            default               -> false;  // Werktage
        };
    }


    public boolean isHalfDay(User user, LocalDate date) {
        // Falls du Halbtage definieren willst
        return false;
    }

    private double getFullTimeHours(User u, LocalDate d) {
        List<Map<String, Double>> sched = u.getWeeklySchedule();
        if (sched != null && u.getScheduleCycle() != null) {
            int idx = (int) (ChronoUnit.WEEKS.between(
                    LocalDate.of(2020, 1, 1), d) % u.getScheduleCycle());
            Double h = sched.get(idx).get(d.getDayOfWeek().name().toLowerCase());
            if (h != null) return h;                 // **noch NICHT prozentual reduziert**
        }
        return (u.getDailyWorkHours() != null) ? u.getDailyWorkHours() : 8.5;
    }

    private double getPercentageFactor(User u) {
        return (Boolean.TRUE.equals(u.getIsPercentage()) && u.getWorkPercentage() != null)
                ? u.getWorkPercentage() / 100.0
                : 1.0;
    }
    private double resolveBaseHours(User user, LocalDate date) {

        // 1. Feste „ab … gültig“-Wochenpläne (shift-cycle)
        if (user.getScheduleCycle() != null &&
                user.getWeeklySchedule() != null &&
                !user.getWeeklySchedule().isEmpty()) {

            int cycleLen   = user.getScheduleCycle();
            LocalDate epoch = LocalDate.of(2020, 1, 1);           // Referenz­beginn
            long index      = ChronoUnit.WEEKS.between(epoch, date) % cycleLen;

            Map<String, Double> week = user.getWeeklySchedule().get((int) index);
            return week.getOrDefault(date.getDayOfWeek().name().toLowerCase(),
                    user.getDailyWorkHours() != null
                            ? user.getDailyWorkHours()
                            : 8.5);
        }

        // 2. Individuell hinterlegte tägliche Soll­stunden
        if (user.getDailyWorkHours() != null) {
            return user.getDailyWorkHours();
        }

        // 3. Fallback
        return 8.5;
    }
    public int computeExpectedWorkMinutes(User user, LocalDate date) {

        double expectedHours = getExpectedWorkHours(user, date);   // schon skaliert

        if (isDayOff(user, date))  return 0;
        if (isHalfDay(user, date)) expectedHours /= 2.0;

        return (int) Math.round(expectedHours * 60);
    }
    }

