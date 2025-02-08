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

    /**
     * Prüft, ob ein bestimmter User an einem Datum 'frei' hat.
     * Je nach 'dayMode' könnte es sein, dass er nur halben Tag arbeitet.
     */
    public boolean isDayOff(User user, LocalDate date) {
        List<UserScheduleRule> rules = ruleRepo.findByUser(user);

        for (UserScheduleRule r : rules) {
            // Bsp: "EVERY_2_WEEKS_FRIDAY_OFF"
            if ("EVERY_2_WEEKS_FRIDAY_OFF".equals(r.getRuleType())) {
                // Prüfe Freitag
                if (date.getDayOfWeek().getValue() == 5) {
                    // Startdate + repeatIntervalDays
                    if (r.getStartDate() == null || !date.isBefore(r.getStartDate())) {
                        if (r.getRepeatIntervalDays() != null) {
                            long diff = ChronoUnit.DAYS.between(r.getStartDate(), date);
                            if (diff >= 0 && diff % r.getRepeatIntervalDays() == 0) {
                                return true; // => Tag ist frei
                            }
                        }
                    }
                }
            }
            // Du kannst weitere if-Blöcke machen,
            // z.B. "EVERY_2_WEEKS_XDAY_OFF" etc.
        }
        return false;
    }

    /**
     * Beispiel: Gibt an, ob nur halber Tag gearbeitet wird.
     */
    public boolean isHalfDay(User user, LocalDate date) {
        List<UserScheduleRule> rules = ruleRepo.findByUser(user);

        for (UserScheduleRule r : rules) {
            // dayMode = "HALF_DAY"
            if ("HALF_DAY".equalsIgnoreCase(r.getDayMode())) {
                if (r.getDayOfWeek() != null && date.getDayOfWeek().getValue() == r.getDayOfWeek()) {
                    // ggf. Intervall checken
                    return true;
                }
            }
        }
        return false;
    }
}
