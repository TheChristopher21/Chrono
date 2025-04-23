package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
public class VacationService {

    private static final Logger logger = LoggerFactory.getLogger(VacationService.class);



    @Autowired
    private VacationRequestRepository vacationRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public VacationRequest createVacationRequest(String username, LocalDate start, LocalDate end, boolean halfDay, boolean usesOvertime) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        VacationRequest vr = new VacationRequest();
        vr.setUser(user);
        vr.setStartDate(start);
        vr.setEndDate(end);
        vr.setApproved(false);
        vr.setDenied(false);
        vr.setHalfDay(halfDay);
        vr.setUsesOvertime(usesOvertime);

        VacationRequest saved = vacationRepo.save(vr);
        logger.info("VacationService: VacationRequest (usesOvertime=" + usesOvertime + ") für User '{}' von {} bis {} erstellt.", username, start, end);

        return saved;
    }


    public VacationRequest adminCreateVacation(String adminUsername, String adminPassword,
                                               String username, LocalDate start, LocalDate end,
                                               boolean halfDay, boolean usesOvertime) {
        checkAdminCredentials(adminUsername, adminPassword);
        VacationRequest vr = createVacationRequest(username, start, end, halfDay, usesOvertime);
        vr.setApproved(true);
        vr.setDenied(false);

        // ✅ Überstunden direkt abziehen
        if (usesOvertime) {
            User user = vr.getUser();
            if (user != null) {
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                double factor = halfDay ? 0.5 : 1.0;

                int dailyMinutes = getDailyVacationMinutes(user);


                int totalMinutes = (int) Math.round(days * factor * dailyMinutes);

                int currentBalance = user.getTrackingBalanceInMinutes() != null
                        ? user.getTrackingBalanceInMinutes()
                        : 0;

                user.setTrackingBalanceInMinutes(currentBalance - totalMinutes);
                userRepo.save(user);

                logger.info("VacationService: (ADMIN) {} Minuten vom Überstundenkonto für '{}' abgezogen ({} Tage, {} min/Tag, halfDay={}, overtime={})",
                        totalMinutes, user.getUsername(), days, dailyMinutes, halfDay, true);
            }
        }

        VacationRequest updated = vacationRepo.save(vr);
        logger.info("VacationService: Admin '{}' hat VacationRequest für '{}' sofort genehmigt.", adminUsername, username);
        return updated;
    }



    public List<VacationRequest> getUserVacations(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return vacationRepo.findByUser(user);
    }

    public VacationRequest approveVacation(Long vacationId) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));

        vr.setApproved(true);
        vr.setDenied(false);

        // ✅ Überstundenabzug nur bei "Überstundenfrei"
        if (vr.isUsesOvertime()) {
            Long userId = vr.getUser().getId();

            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            LocalDate start = vr.getStartDate();
            LocalDate end = vr.getEndDate();
            boolean isHalfDay = vr.isHalfDay();

            // Anzahl Urlaubstage berechnen (inkl. Start+Ende)
            long days = ChronoUnit.DAYS.between(start, end) + 1;

            // Wenn Halbtags, nur 1 Tag erlaubt
            if (isHalfDay) {
                days = 1;
            }

            // Minuten pro Tag berechnen anhand workPercentage
            int dailyMinutes = getDailyVacationMinutes(user);
            if (user.getWorkPercentage() != null) {
                dailyMinutes = (int) Math.round((user.getWorkPercentage() / 100.0) * 480);
            }

            int totalMinutes = (int) (days * dailyMinutes);

            // Vorheriger Stand
            int currentBalance = user.getTrackingBalanceInMinutes() != null ? user.getTrackingBalanceInMinutes() : 0;

            logger.info("Urlaub genehmigt für '{}': {} bis {} ({} Tage, {} min/Tag, usesOvertime={}, halfDay={})",
                    user.getUsername(), start, end, days, dailyMinutes, true, isHalfDay);
            logger.info("TrackingBalance vorher: {}", currentBalance);

            // Abziehen
            int newBalance = currentBalance - totalMinutes;
            user.setTrackingBalanceInMinutes(newBalance);

            logger.info("TrackingBalance nachher: {}", newBalance);

            userRepo.save(user);
        }

        VacationRequest updated = vacationRepo.save(vr);
        logger.info("VacationService: VacationRequest ID {} genehmigt.", vacationId);
        return updated;
    }


    private int getDailyVacationMinutes(User user) {
        if (!user.getIsPercentage()) {
            logger.info("🧮 {} ist kein Prozent-User → default 510min/Tag", user.getUsername());
            return 510;
        }

        double weeklyMinutes = 42.5 * 60 * (user.getWorkPercentage() / 100.0);
        int workdays = 0;

        try {
            List<Map<String, Double>> scheduleList = user.getWeeklySchedule();
            if (scheduleList != null) {
                for (Map<String, Double> map : scheduleList) {
                    for (Map.Entry<String, Double> entry : map.entrySet()) {
                        String day = entry.getKey();
                        Double value = entry.getValue();

                        if (value != null && value > 0.0) {
                            workdays++;
                            logger.info("✅ {} arbeitet an {} ({}h)", user.getUsername(), day, value);
                        } else {
                            logger.info("❌ {} arbeitet NICHT an {} ({}h)", user.getUsername(), day, value);
                        }
                    }
                }
            } else {
                logger.warn("⚠️ weeklySchedule ist NULL bei {}", user.getUsername());
            }
        } catch (Exception e) {
            logger.error("💥 Fehler beim Lesen des Wochenplans von {}: {}", user.getUsername(), e.getMessage());
        }

        if (workdays == 0) {
            workdays = 5;
            logger.warn("⚠️ Keine Arbeitstage erkannt – fallback auf 5 Tage für {}", user.getUsername());
        }

        int result = (int) Math.round(weeklyMinutes / workdays);
        logger.info("📊 {}: {}min/Woche bei {} Arbeitstagen → Urlaubstag = {}min",
                user.getUsername(), (int) weeklyMinutes, workdays, result);

        return result;
    }


    public VacationRequest denyVacation(Long vacationId) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));
        vr.setApproved(false);
        vr.setDenied(true);
        VacationRequest updated = vacationRepo.save(vr);
        logger.info("VacationService: VacationRequest ID {} abgelehnt.", vacationId);
        return updated;
    }

    /**
     * Löscht einen VacationRequest (für einen einzelnen Eintrag).
     */
    public VacationRequest deleteVacation(Long vacationId, String adminUsername, String adminPassword) {
        checkAdminCredentials(adminUsername, adminPassword);
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));
        vacationRepo.delete(vr);
        logger.info("VacationService: VacationRequest ID {} wurde gelöscht.", vacationId);
        return vr;
    }

    /**
     * Neue Methode: Löscht alle VacationRequests für einen gegebenen Benutzer.
     * Dies ist nützlich, bevor ein Benutzer gelöscht wird.
     */
    @Transactional
    public void deleteVacationsByUser(User user) {
        logger.info("VacationService: Lösche alle VacationRequests für Benutzer '{}'.", user.getUsername());
        vacationRepo.deleteByUser(user);
    }

    // Überprüfung der Admin-Zugangsdaten
    public void checkAdminCredentials(String adminUsername, String adminPassword) {
        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin user not found"));
        if (!admin.getRoles().stream().anyMatch(role -> role.getRoleName().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized: not an admin");
        }
        if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            throw new RuntimeException("Unauthorized: invalid password");
        }
    }

    public List<VacationRequest> getAllVacations() {
        return vacationRepo.findAll();
    }

    /**
     * Berechnet den verbleibenden Urlaubsanspruch.
     * An offiziellen Feiertagen wird kein Urlaubstag abgezogen.
     */
    public double calculateRemainingVacationDays(String username, int year) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        int annualVacationDays = (user.getAnnualVacationDays() != null) ? user.getAnnualVacationDays() : 25;
        List<VacationRequest> vacations = getUserVacations(username);
        double usedDays = 0.0;
        Set<LocalDate> holidays = getHolidays(year);
        for (VacationRequest vr : vacations) {
            if (vr.isApproved()) {
                LocalDate start = vr.getStartDate();
                LocalDate end = vr.getEndDate();
                if (start.getYear() == year || end.getYear() == year) {
                    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                        if (!holidays.contains(date)) {
                            usedDays += vr.isHalfDay() ? 0.5 : 1.0;
                        }
                    }
                }
            }
        }
        logger.info("VacationService: Benutzer '{}' hat {}/{} Urlaubstage genutzt.", username, usedDays, annualVacationDays);
        return annualVacationDays - usedDays;
    }

    /**
     * Liefert ein Set aller offiziellen Feiertage für das angegebene Jahr.
     */
    private Set<LocalDate> getHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(year, 1, 1)); // Neujahr

        LocalDate easterSunday = calculateEasterSunday(year);
        holidays.add(easterSunday.minusDays(2)); // Karfreitag
        holidays.add(easterSunday.plusDays(1));   // Ostermontag

        holidays.add(LocalDate.of(year, 5, 1)); // 1. Mai
        holidays.add(easterSunday.plusDays(39)); // Auffahrt
        holidays.add(easterSunday.plusDays(50)); // Pfingstmontag
        holidays.add(LocalDate.of(year, 8, 1));  // Nationalfeiertag

        holidays.add(LocalDate.of(year, 10, 16)); // Optional: Gallustag
        holidays.add(LocalDate.of(year, 12, 25)); // Weihnachten
        holidays.add(LocalDate.of(year, 12, 26)); // Stephanstag

        return holidays;
    }

    /**
     * Berechnet das Osterdatum (Ostersonntag) für das angegebene Jahr (Gauss-Algorithmus).
     */
    private LocalDate calculateEasterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
