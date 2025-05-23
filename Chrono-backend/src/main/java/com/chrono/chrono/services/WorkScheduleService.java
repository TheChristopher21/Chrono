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

    public double getExpectedWorkHours(User user, LocalDate date) {
        // Z.B. per user.getDailyWorkHours()
        // Oder wochenplanbasierte Logik (wenn du die in JSON ablegst)
        // Fallback 8.5 Std
        if (user.getScheduleEffectiveDate() != null && date.isBefore(user.getScheduleEffectiveDate())) {
            return (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
        }

        // Falls du einen cycle-basierten Plan hast, könntest du den hier verarbeiten …
        // Sonst fallback:
        return (user.getDailyWorkHours() != null) ? user.getDailyWorkHours() : 8.5;
    }

    public boolean isDayOff(User user, LocalDate date) {
        // Falls du bestimmte Ausnahmen hast (UserScheduleRule),
        // z.B. "jeden 2. Freitag frei" => Return true
        List<UserScheduleRule> rules = ruleRepo.findByUser(user);
        // Dummy:
        return false;
    }

    public boolean isHalfDay(User user, LocalDate date) {
        // Falls du Halbtage definieren willst
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
