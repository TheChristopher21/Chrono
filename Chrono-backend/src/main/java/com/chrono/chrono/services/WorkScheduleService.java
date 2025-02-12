package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserScheduleRule;
import com.chrono.chrono.repositories.UserScheduleRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class WorkScheduleService {

    @Autowired
    private UserScheduleRuleRepository ruleRepo;

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
        // Nehme die täglichen Arbeitsstunden aus dem User-Objekt; Standard: 8 Stunden
        double dailyWorkHours = (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.0;
        if (isDayOff(user, date)) {
            return 0;
        } else if (isHalfDay(user, date)) {
            return (int) Math.round((dailyWorkHours * 60) / 2);
        } else {
            return (int) Math.round(dailyWorkHours * 60);
        }
    }
}
