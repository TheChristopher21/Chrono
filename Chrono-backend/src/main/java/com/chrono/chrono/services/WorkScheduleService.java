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

    /**
     * Berechnet die erwarteten Arbeitsstunden basierend auf dem individuellen Wochenplan.
     * Hierbei wird davon ausgegangen, dass das Feld weeklySchedule in der User-Entity
     * bereits als List<Map<String, Integer>> vorliegt (mittels eines AttributeConverters).
     *
     * Falls keine individuelle Konfiguration vorhanden ist, wird der Wert aus dailyWorkHours
     * oder 8 Stunden als Fallback verwendet.
     */
    public double getExpectedWorkHours(User user, LocalDate date) {
        // Wenn ein effektives Datum gesetzt ist und das Datum vor diesem liegt,
        // verwenden wir den Fallback (dailyWorkHours oder 8 Stunden).
        if (user.getScheduleEffectiveDate() != null && date.isBefore(user.getScheduleEffectiveDate())) {
            return (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.0;
        }
        List<Map<String, Integer>> schedule = user.getWeeklySchedule();
        if (schedule != null && user.getScheduleCycle() != null) {
            try {
                int cycleLength = user.getScheduleCycle();
                LocalDate epoch = LocalDate.of(2020, 1, 1);
                long diffWeeks = ChronoUnit.WEEKS.between(epoch, date);
                int index = (int)(diffWeeks % cycleLength);
                Map<String, Integer> weekSchedule = schedule.get(index);
                String dayOfWeek = date.getDayOfWeek().toString().toLowerCase();
                if (weekSchedule.containsKey(dayOfWeek)) {
                    return weekSchedule.get(dayOfWeek);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.0;
    }

    public boolean isDayOff(User user, LocalDate date) {
        List<UserScheduleRule> rules = ruleRepo.findByUser(user);
        for (UserScheduleRule r : rules) {
            if ("EVERY_2_WEEKS_FRIDAY_OFF".equals(r.getRuleType())) {
                if (date.getDayOfWeek().getValue() == 5) { // Freitag
                    if (r.getStartDate() == null || !date.isBefore(r.getStartDate())) {
                        if (r.getRepeatIntervalDays() != null) {
                            long diff = ChronoUnit.DAYS.between(r.getStartDate(), date);
                            if (diff >= 0 && diff % r.getRepeatIntervalDays() == 0) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isHalfDay(User user, LocalDate date) {
        List<UserScheduleRule> rules = ruleRepo.findByUser(user);
        for (UserScheduleRule r : rules) {
            if ("HALF_DAY".equalsIgnoreCase(r.getDayMode())) {
                if (r.getDayOfWeek() != null && date.getDayOfWeek().getValue() == r.getDayOfWeek()) {
                    return true;
                }
            }
        }
        return false;
    }

    public int computeExpectedWorkMinutes(User user, LocalDate date) {
        double expectedHours = getExpectedWorkHours(user, date);
        if (isDayOff(user, date)) {
            return 0;
        } else if (isHalfDay(user, date)) {
            return (int) Math.round((expectedHours * 60) / 2);
        } else {
            return (int) Math.round(expectedHours * 60);
        }
    }
}
