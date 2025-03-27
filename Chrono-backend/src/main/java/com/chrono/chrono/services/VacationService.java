// src/main/java/com/chrono/chrono/services/VacationService.java
package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
public class VacationService {

    @Autowired
    private VacationRequestRepository vacationRepo;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public VacationRequest createVacationRequest(String username, LocalDate start, LocalDate end, boolean halfDay) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        VacationRequest vr = new VacationRequest();
        vr.setUser(user);
        vr.setStartDate(start);
        vr.setEndDate(end);
        vr.setApproved(false);
        vr.setDenied(false);
        vr.setHalfDay(halfDay);
        return vacationRepo.save(vr);
    }

    // Admin erstellt Urlaub – sofort genehmigt
    public VacationRequest adminCreateVacation(String adminUsername, String adminPassword, String username, LocalDate start, LocalDate end, boolean halfDay) {
        checkAdminCredentials(adminUsername, adminPassword);
        VacationRequest vr = createVacationRequest(username, start, end, halfDay);
        vr.setApproved(true);
        vr.setDenied(false);
        return vacationRepo.save(vr);
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
        return vacationRepo.save(vr);
    }

    public VacationRequest denyVacation(Long vacationId) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));
        vr.setApproved(false);
        vr.setDenied(true);
        return vacationRepo.save(vr);
    }

    public VacationRequest deleteVacation(Long vacationId, String adminUsername, String adminPassword) {
        checkAdminCredentials(adminUsername, adminPassword);
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));
        vacationRepo.delete(vr);
        return vr;
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
        return annualVacationDays - usedDays;
    }

    /**
     * Liefert ein Set aller offiziellen Feiertage für das angegebene Jahr.
     * Enthalten sind Neujahr, Karfreitag, Ostermontag, 1. Mai, Auffahrt, Pfingstmontag,
     * Nationalfeiertag, optional Gallustag (z. B. 16.10.), Weihnachten und Stephanstag.
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

        // Optional: Gallustag (z.B. 16.10.) – passe dies an, wenn für deinen Betrieb relevant
        holidays.add(LocalDate.of(year, 10, 16));

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
