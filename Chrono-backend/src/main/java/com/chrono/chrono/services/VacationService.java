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
import java.util.*;

@Service
public class VacationService {

    private static final Logger logger = LoggerFactory.getLogger(VacationService.class);

    @Autowired
    private VacationRequestRepository vacationRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // (NEU) Evtl. nützlich, um an anderer Stelle User-Checks zu machen
    @Autowired
    private UserService userService; // Falls du so was hast, s. u.

    /**
     * Standard-Urlaubsantrag. Weist dem User = username zu; Firma leitet sich
     * aus user.getCompany() ab. (Keine extra company-Spalte in VacationRequest?)
     */
    public VacationRequest createVacationRequest(String username, LocalDate start, LocalDate end,
                                                 boolean halfDay, boolean usesOvertime)
    {
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

        // (NEU) Falls du company-Feld in VacationRequest hättest, hier:
        // vr.setCompany(user.getCompany());

        VacationRequest saved = vacationRepo.save(vr);
        logger.info("VacationService: VacationRequest (usesOvertime={}) für User '{}' von {} bis {} erstellt.",
                usesOvertime, username, start, end);
        return saved;
    }


    /**
     * Admin legt direkt Urlaub für User an.
     * -> Überprüft Admin-Zugang + selbe Firma,
     * -> Erzeugt Request + genehmigt ihn sofort,
     * -> Zieht ggf. Überstunden ab.
     */
    public VacationRequest adminCreateVacation(String adminUsername, String adminPassword,
                                               String username, LocalDate start, LocalDate end,
                                               boolean halfDay, boolean usesOvertime)
    {
        checkAdminCredentials(adminUsername, adminPassword);

        // (NEU) check: admin und user in derselben Firma?
        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin user not found"));
        User targetUser = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Target user not found"));

        // => wirf Exception, wenn Firmen nicht übereinstimmen
        if (!admin.getCompany().getId().equals(targetUser.getCompany().getId())) {
            throw new RuntimeException("Admin + User sind in unterschiedlichen Firmen!");
        }

        // Normalen Request anlegen
        VacationRequest vr = createVacationRequest(username, start, end, halfDay, usesOvertime);
        vr.setApproved(true);
        vr.setDenied(false);

        // Überstunden direkt abziehen
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
        logger.info("VacationService: Admin '{}' hat VacationRequest für '{}' sofort genehmigt.",
                adminUsername, username);
        return updated;
    }


    /**
     * Liefert alle Urlaube des Benutzers.
     * -> Falls du "myVacations" als normaler User aufrufst,
     *    hast du ja "principal.name" = user.
     */
    public List<VacationRequest> getUserVacations(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return vacationRepo.findByUser(user);
    }

    /**
     * (NEU) -> Alle Urlaube in der Firma, z. B. Admin-Sicht
     *    Du brauchst in VacationRequestRepository:
     *    List<VacationRequest> findByUser_Company_Id(Long companyId);
     */
    public List<VacationRequest> getAllVacationsInCompany(Long companyId) {
        return vacationRepo.findByUser_Company_Id(companyId);
    }

    // Minimalvariante: Admin ruft => genehmigt den Request
    // => check, ob Admin und Request im selben Tenant
    public VacationRequest approveVacation(Long vacationId, String adminName) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));
        User admin = userRepo.findByUsername(adminName)
                .orElseThrow(() -> new UserNotFoundException("Admin not found"));

        // user der Request
        User user = vr.getUser();
        if (!admin.getCompany().getId().equals(user.getCompany().getId())) {
            throw new RuntimeException("Keine Berechtigung: Verschiedene Firmen!");
        }

        vr.setApproved(true);
        vr.setDenied(false);

        // ggf. Überstunden abziehen
        if (vr.isUsesOvertime()) {
            applyOvertimeDeduction(vr);
        }

        VacationRequest updated = vacationRepo.save(vr);
        logger.info("VacationService: VacationRequest ID {} genehmigt von Admin '{}'.", vacationId, adminName);
        return updated;
    }


    // Original: ApproveVacation(Long vacationId)
    // => wir fügen param adminName
    // oder du kannst: public VacationRequest approveVacation(Long vacationId) { ... }
    //   in VacationController param: (VacationId, principal)

    /**
     * Hilfsmethode: z. B. wenn wir genehmigen => Overtime abziehen
     */
    private void applyOvertimeDeduction(VacationRequest vr) {
        LocalDate start = vr.getStartDate();
        LocalDate end   = vr.getEndDate();
        boolean isHalfDay = vr.isHalfDay();
        User user = vr.getUser();

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (isHalfDay) {
            days = 1;
        }
        int dailyMinutes = getDailyVacationMinutes(user);
        int totalMinutes = (int)(days * dailyMinutes);

        int currentBalance = user.getTrackingBalanceInMinutes() != null
                ? user.getTrackingBalanceInMinutes()
                : 0;
        int newBalance = currentBalance - totalMinutes;
        user.setTrackingBalanceInMinutes(newBalance);

        userRepo.save(user);
        logger.info("Urlaub genehmigt (Overtime). {}min abgezogen. Neu = {}min. ({} Tage, halfDay={})",
                totalMinutes, newBalance, days, isHalfDay);
    }


    /**
     * Alte Version - genehmigen OHNE adminName param.
     *   => Achtung, hier fehlt die MultiTenant-Sicherheit.
     */
    public VacationRequest approveVacation(Long vacationId) {
        // Du könntest hier => throw new UnsupportedOperationException
        // oder beibehalten,
        // aber dann fehlt "Welche Firma??"
        // ...
        return null; // dummy
    }


    public VacationRequest denyVacation(Long vacationId, String adminName) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));
        User admin = userRepo.findByUsername(adminName)
                .orElseThrow(() -> new UserNotFoundException("Admin not found"));

        if (!admin.getCompany().getId().equals(vr.getUser().getCompany().getId())) {
            throw new RuntimeException("Different companies - no permission!");
        }

        vr.setApproved(false);
        vr.setDenied(true);
        VacationRequest updated = vacationRepo.save(vr);
        logger.info("VacationService: VacationRequest ID {} abgelehnt von Admin '{}'.", vacationId, adminName);
        return updated;
    }

    /**
     * Bisherige deleteVacation mit Admin-Check + passwort
     */
    public VacationRequest deleteVacation(Long vacationId, String adminUsername, String adminPassword) {
        checkAdminCredentials(adminUsername, adminPassword);

        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));

        // => Company Check
        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin user not found"));
        if (!admin.getCompany().getId().equals(vr.getUser().getCompany().getId())) {
            throw new RuntimeException("Keine Berechtigung (versch. Firmen).");
        }

        vacationRepo.delete(vr);
        logger.info("VacationService: VacationRequest ID {} wurde gelöscht (admin='{}').", vacationId, adminUsername);
        return vr;
    }

    /**
     * Sammelt alle VacationRequests -> Du kannst das so belassen,
     * oder auf Filtern umstellen => getAllVacationsInCompany
     */
    public List<VacationRequest> getAllVacations() {
        return vacationRepo.findAll();
    }

    // (NEU) -> In "VacationsController" o.ä. => man kann "getAllVacationsInCompany" aufrufen.

    /**
     * Hilfsmethode, um Admin zu verifizieren.
     * Beachte: wir checken NICHT company,
     * das kannst du ggf. hier oder in
     * adminCreateVacation machen.
     */
    public void checkAdminCredentials(String adminUsername, String adminPassword) {
        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin user not found"));

        if (admin.getRoles().stream().noneMatch(role -> role.getRoleName().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized: not an admin");
        }
        if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            throw new RuntimeException("Unauthorized: invalid password");
        }
    }

    // ============ HILFSMETHODEN FÜR URLAUBSBERECHNUNG ==================

    public double calculateRemainingVacationDays(String username, int year) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        int annualVacationDays = (user.getAnnualVacationDays() != null)
                ? user.getAnnualVacationDays() : 25;
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
        logger.info("VacationService: Benutzer '{}' hat {}/{} Urlaubstage genutzt.",
                username, usedDays, annualVacationDays);
        return annualVacationDays - usedDays;
    }

    @Transactional
    public void deleteVacationsByUser(User user) {
        logger.info("VacationService: Lösche alle VacationRequests für Benutzer '{}'.", user.getUsername());
        vacationRepo.deleteByUser(user);
    }


    private Set<LocalDate> getHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(year, 1, 1)); // Neujahr
        LocalDate easterSunday = calculateEasterSunday(year);
        holidays.add(easterSunday.minusDays(2)); // Karfreitag
        holidays.add(easterSunday.plusDays(1));  // Ostermontag
        holidays.add(LocalDate.of(year, 5, 1));  // 1. Mai
        holidays.add(easterSunday.plusDays(39)); // Auffahrt
        holidays.add(easterSunday.plusDays(50)); // Pfingstmontag
        holidays.add(LocalDate.of(year, 8, 1));  // Nationalfeiertag
        holidays.add(LocalDate.of(year, 12, 25)); // Weihnachten
        holidays.add(LocalDate.of(year, 12, 26)); // Stephanstag
        return holidays;
    }

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
        int l = (32 + 2*e + 2*i - h - k) % 7;
        int m = (a + 11*h + 22*l) / 451;
        int month = (h + l - 7*m + 114) / 31;
        int day = ((h + l - 7*m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    /**
     * Ermittelt, wie viele Minuten pro Urlaubstag abgezogen werden (z. B. 8.5h).
     * Falls du schon isPercentage= false => 8.5h => 510min
     * Falls prozentual => weeklySchedule / 42.5h ...
     */
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

}
