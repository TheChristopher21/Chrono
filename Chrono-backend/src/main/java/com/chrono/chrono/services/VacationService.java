// Datei: com/chrono/chrono/services/VacationService.java
package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class VacationService {

    private static final Logger logger = LoggerFactory.getLogger(VacationService.class); //
    private static final double BASE_FULL_TIME_WEEKLY_HOURS = 42.5; //


    @Autowired
    private VacationRequestRepository vacationRepo; //

    @Autowired
    private UserRepository userRepo; //

    @Autowired
    private UserService userService; //

    @Autowired
    private WorkScheduleService workScheduleService; //

    @Autowired // NEU: HolidayService injizieren
    private HolidayService holidayService;


    public VacationRequest createVacationRequest(String username, LocalDate start, LocalDate end,
                                                 boolean halfDay, boolean usesOvertime,
                                                 Integer overtimeDeductionMinutes) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username)); //

        if (end.isBefore(start)) { //
            throw new IllegalArgumentException("Das Enddatum darf nicht vor dem Startdatum liegen."); //
        }
        if (halfDay && !start.isEqual(end)) { //
            throw new IllegalArgumentException("Halbtags Urlaub kann nur für einen einzelnen Tag beantragt werden."); //
        }

        VacationRequest vr = new VacationRequest(); //
        vr.setUser(user); //
        vr.setStartDate(start); //
        vr.setEndDate(end); //
        vr.setApproved(false); //
        vr.setDenied(false); //
        vr.setHalfDay(halfDay); //
        vr.setUsesOvertime(usesOvertime); //

        if (usesOvertime && Boolean.TRUE.equals(user.getIsPercentage())) { //
            if (overtimeDeductionMinutes != null && overtimeDeductionMinutes > 0) { //
                vr.setOvertimeDeductionMinutes(overtimeDeductionMinutes); //
            } else {
                vr.setOvertimeDeductionMinutes(null); //
                logger.warn("User {} (prozentual) hat Überstundenurlaub ohne gültige Abzugsminuten beantragt. `overtimeDeductionMinutes` wird auf NULL gesetzt.", username); //
            }
        } else if (usesOvertime && !Boolean.TRUE.equals(user.getIsPercentage())) { //
            vr.setOvertimeDeductionMinutes(null); //
        } else {
            vr.setOvertimeDeductionMinutes(null); //
        }
        VacationRequest saved = vacationRepo.save(vr); //
        logger.info("VacationService: VacationRequest (ID: {}) für User '{}' von {} bis {} erstellt (usesOvertime={}, halfDay={}, overtimeDeductionMinutes={}).",
                saved.getId(), username, start, end, usesOvertime, halfDay, vr.getOvertimeDeductionMinutes()); //
        return saved; //
    }

    @Transactional
    public VacationRequest adminCreateVacation(String adminUsername,
                                               String targetUsername, LocalDate start, LocalDate end,
                                               boolean halfDay, boolean usesOvertime,
                                               Integer overtimeDeductionMinutesParam) {
        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin user " + adminUsername + " not found")); //
        User targetUser = userRepo.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("Target user " + targetUsername + " not found")); //

        boolean isSuperAdmin = admin.getRoles().stream().anyMatch(role -> role.getRoleName().equals("ROLE_SUPERADMIN")); //
        if (!isSuperAdmin) { //
            Company adminCompany = admin.getCompany(); //
            Company targetCompany = targetUser.getCompany(); //

            if (adminCompany == null) { //
                throw new SecurityException("Admin user " + adminUsername + " is not assigned to any company and cannot manage company-specific vacations."); //
            }
            if (targetCompany == null || !adminCompany.getId().equals(targetCompany.getId())) { //
                throw new SecurityException("Admin und Zielbenutzer gehören nicht zur selben Firma, oder die Firmen-ID des Zielbenutzers ist nicht gesetzt."); //
            }
        }

        if (end.isBefore(start)) { //
            throw new IllegalArgumentException("Das Enddatum darf nicht vor dem Startdatum liegen."); //
        }
        if (halfDay && !start.isEqual(end)) { //
            throw new IllegalArgumentException("Halbtags Urlaub kann nur für einen einzelnen Tag beantragt werden."); //
        }

        VacationRequest vr = new VacationRequest(); //
        vr.setUser(targetUser); //
        vr.setStartDate(start); //
        vr.setEndDate(end); //
        vr.setApproved(true); //
        vr.setDenied(false); //
        vr.setHalfDay(halfDay); //
        vr.setUsesOvertime(usesOvertime); //
        vr.setOvertimeDeductionMinutes(null); //

        if (usesOvertime && Boolean.TRUE.equals(targetUser.getIsPercentage()) && overtimeDeductionMinutesParam != null && overtimeDeductionMinutesParam > 0) { //
            vr.setOvertimeDeductionMinutes(overtimeDeductionMinutesParam); //
            logger.info("Admin create for percentage user {}: overtimeDeductionMinutes set to {} for the period.", targetUser.getUsername(), overtimeDeductionMinutesParam); //
        }


        VacationRequest savedVr = vacationRepo.save(vr); //

        if (usesOvertime && !Boolean.TRUE.equals(targetUser.getIsHourly())) { //
            applyOvertimeDeduction(savedVr); //
        }

        logger.info("VacationService: Admin '{}' hat VacationRequest ID {} für '{}' erstellt und genehmigt (usesOvertime={}, deductionMinutes={}).",
                adminUsername, savedVr.getId(), targetUsername, usesOvertime, savedVr.getOvertimeDeductionMinutes()); //
        return savedVr; //
    }

    @Transactional
    public VacationRequest approveVacation(Long vacationId, String adminName) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request " + vacationId + " not found")); //
        User admin = userRepo.findByUsername(adminName)
                .orElseThrow(() -> new UserNotFoundException("Admin user " + adminName + " not found")); //

        User user = vr.getUser(); //
        boolean isSuperAdmin = admin.getRoles().stream().anyMatch(role -> role.getRoleName().equals("ROLE_SUPERADMIN")); //
        if (!isSuperAdmin) { //
            Company adminCompany = admin.getCompany(); //
            Company userCompany = user.getCompany(); //
            if (adminCompany == null || userCompany == null || !adminCompany.getId().equals(userCompany.getId())) { //
                throw new SecurityException("Keine Berechtigung: Admin und Benutzer des Urlaubsantrags gehören nicht zur selben Firma oder eine der Firmen ist null."); //
            }
        }


        if (vr.isApproved() || vr.isDenied()){ //
            logger.warn("Versuch, bereits bearbeiteten Urlaubsantrag ID {} erneut zu genehmigen.", vacationId); //
            return vr; //
        }

        vr.setApproved(true); //
        vr.setDenied(false); //

        if (vr.isUsesOvertime() && (user.getIsHourly() == null || !user.getIsHourly())) { //
            applyOvertimeDeduction(vr); //
        }


        VacationRequest updated = vacationRepo.save(vr); //
        logger.info("VacationService: VacationRequest ID {} genehmigt von Admin '{}'.", vacationId, adminName); //
        return updated; //
    }

    private void applyOvertimeDeduction(VacationRequest vr) {
        User user = vr.getUser(); //

        if (user == null) { //
            logger.error("VacationRequest ID {} hat keinen zugeordneten User für applyOvertimeDeduction.", vr.getId()); //
            return; //
        }
        if (Boolean.TRUE.equals(user.getIsHourly())) { //
            logger.info("Urlaub via Überstunden für stundenbasierten Mitarbeiter '{}' (Antrag ID {}). Kein Abzug vom Saldo.", user.getUsername(), vr.getId()); //
            return; //
        }

        LocalDate start = vr.getStartDate(); //
        LocalDate end = vr.getEndDate(); //
        boolean isHalfDay = vr.isHalfDay(); //
        long actualWorkDaysInVacationPeriod = 0; //
        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;


        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) { //
            // workScheduleService.isDayOff berücksichtigt Wochenenden UND Feiertage
            if (!workScheduleService.isDayOff(user, date)) { //
                actualWorkDaysInVacationPeriod++; //
            }
        }

        if (actualWorkDaysInVacationPeriod <= 0) { //
            logger.info("Keine abzuziehenden Arbeitstage im Urlaubszeitraum für Antrag ID {} (User: {}). Saldo bleibt unverändert.", vr.getId(), user.getUsername()); //
            return; //
        }

        int totalMinutesToDeduct; //

        if (Boolean.TRUE.equals(user.getIsPercentage()) && vr.isUsesOvertime() && vr.getOvertimeDeductionMinutes() != null && vr.getOvertimeDeductionMinutes() > 0) { //
            totalMinutesToDeduct = vr.getOvertimeDeductionMinutes(); //
            logger.info("Verwende spezifische overtimeDeductionMinutes ({}) für prozentualen User {} (Antrag ID {}).", totalMinutesToDeduct, user.getUsername(), vr.getId()); //
        } else {
            int dailyMinutesValue = getDailyVacationMinutes(user); //

            if (isHalfDay && actualWorkDaysInVacationPeriod == 1) { //
                totalMinutesToDeduct = (int) Math.round(0.5 * dailyMinutesValue); //
            } else {
                totalMinutesToDeduct = (int) Math.round(actualWorkDaysInVacationPeriod * 1.0 * dailyMinutesValue); //
            }
            if (vr.isUsesOvertime() && vr.getOvertimeDeductionMinutes() == null) { //
                vr.setOvertimeDeductionMinutes(totalMinutesToDeduct); //
            }
        }


        int currentBalance = user.getTrackingBalanceInMinutes() != null ? user.getTrackingBalanceInMinutes() : 0; //
        int newBalance = currentBalance - totalMinutesToDeduct; //
        user.setTrackingBalanceInMinutes(newBalance); //
        userRepo.save(user); //

        logger.info("Urlaub (Antrag ID {}) für '{}' genehmigt (usesOvertime={}). {}min abgezogen. Neuer Saldo: {}min. (Berechnungsbasis: {} Arbeitstage, Halbtag: {}, spezifische Minuten verwendet: {})",
                vr.getId(), user.getUsername(), vr.isUsesOvertime(), totalMinutesToDeduct, newBalance, actualWorkDaysInVacationPeriod, isHalfDay,
                (Boolean.TRUE.equals(user.getIsPercentage()) && vr.getOvertimeDeductionMinutes() != null && vr.getOvertimeDeductionMinutes().equals(totalMinutesToDeduct))); //
    }


    public List<VacationRequest> getUserVacations(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username)); //
        return vacationRepo.findByUser(user); //
    }

    public List<VacationRequest> getAllVacationsInCompany(Long companyId) {
        if (companyId == null) { //
            logger.warn("getAllVacationsInCompany aufgerufen mit companyId null."); //
            return Collections.emptyList(); //
        }
        return vacationRepo.findByUser_Company_Id(companyId); //
    }

    @Transactional
    public VacationRequest denyVacation(Long vacationId, String adminName) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request " + vacationId + " not found")); //
        User admin = userRepo.findByUsername(adminName)
                .orElseThrow(() -> new UserNotFoundException("Admin user " + adminName + " not found")); //
        User user = vr.getUser(); //

        boolean isSuperAdmin = admin.getRoles().stream().anyMatch(role -> role.getRoleName().equals("ROLE_SUPERADMIN")); //
        if (!isSuperAdmin) { //
            Company adminCompany = admin.getCompany(); //
            Company userCompany = user.getCompany(); //
            if (adminCompany == null || userCompany == null || !adminCompany.getId().equals(userCompany.getId())) { //
                throw new SecurityException("Keine Berechtigung: Admin und Benutzer des Urlaubsantrags gehören nicht zur selben Firma oder eine der Firmen ist null."); //
            }
        }

        if (vr.isApproved() || vr.isDenied()){ //
            logger.warn("Versuch, bereits bearbeiteten Urlaubsantrag ID {} erneut abzulehnen.", vacationId); //
            return vr; //
        }
        vr.setApproved(false); //
        vr.setDenied(true); //
        VacationRequest updated = vacationRepo.save(vr); //
        logger.info("VacationService: VacationRequest ID {} abgelehnt von Admin '{}'.", vacationId, adminName); //
        return updated; //
    }

    @Transactional
    public VacationRequest deleteVacation(Long vacationId, String adminUsername) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request " + vacationId + " not found")); //

        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin user " + adminUsername + " not found")); //
        User user = vr.getUser(); //

        if (user == null) { //
            logger.error("Fehler beim Löschen von Urlaub ID {}: Kein zugehöriger Benutzer gefunden. Urlaub wird trotzdem gelöscht.", vacationId); //
            vacationRepo.delete(vr); //
            VacationRequest deletedVr = new VacationRequest(); //
            deletedVr.setId(vacationId); //
            return deletedVr; //
        }


        boolean isSuperAdmin = admin.getRoles().stream().anyMatch(role -> role.getRoleName().equals("ROLE_SUPERADMIN")); //
        if (!isSuperAdmin) { //
            Company adminCompany = admin.getCompany(); //
            Company userCompany = user.getCompany(); //
            if (adminCompany == null || userCompany == null || !adminCompany.getId().equals(userCompany.getId())) { //
                throw new SecurityException("Keine Berechtigung (versch. Firmen oder Firmen-ID ist null)."); //
            }
        }

        if (vr.isApproved() && vr.isUsesOvertime() && (user.getIsHourly() == null || !user.getIsHourly())) { //
            int minutesToRestore; //
            if (vr.getOvertimeDeductionMinutes() != null && vr.getOvertimeDeductionMinutes() > 0) { //
                minutesToRestore = vr.getOvertimeDeductionMinutes(); //
                logger.info("Lösche genehmigten Überstundenurlaub ID {}: Stelle {} Minuten (aus vr.getOvertimeDeductionMinutes()) für User '{}' wieder her.", vacationId, minutesToRestore, user.getUsername()); //
            } else {
                long actualWorkDaysInVacationPeriod = 0; //
                for (LocalDate date = vr.getStartDate(); !date.isAfter(vr.getEndDate()); date = date.plusDays(1)) { //
                    // workScheduleService.isDayOff berücksichtigt Feiertage
                    if (!workScheduleService.isDayOff(user, date)) { //
                        actualWorkDaysInVacationPeriod++; //
                    }
                }
                if (actualWorkDaysInVacationPeriod > 0) { //
                    int dailyMinutesValue = getDailyVacationMinutes(user); //
                    minutesToRestore = (int) Math.round(actualWorkDaysInVacationPeriod * (vr.isHalfDay() && actualWorkDaysInVacationPeriod == 1 ? 0.5 : 1.0) * dailyMinutesValue); //
                    logger.info("Lösche genehmigten Überstundenurlaub ID {}: Stelle {} Minuten (berechnet) für User '{}' wieder her.", vacationId, minutesToRestore, user.getUsername()); //
                } else {
                    minutesToRestore = 0; //
                    logger.info("Lösche genehmigten Überstundenurlaub ID {}: Keine Arbeitstage im Zeitraum, keine Minuten für User '{}' wiederhergestellt.", vacationId, user.getUsername()); //
                }
            }

            if (minutesToRestore > 0) { //
                int currentBalance = user.getTrackingBalanceInMinutes() != null ? user.getTrackingBalanceInMinutes() : 0; //
                user.setTrackingBalanceInMinutes(currentBalance + minutesToRestore); //
                userRepo.save(user); //
                logger.info("VacationService: Genehmigter Überstundenurlaub ID {} gelöscht. {} Minuten wurden User '{}' gutgeschrieben. Neuer Saldo: {}.", vacationId, minutesToRestore, user.getUsername(), user.getTrackingBalanceInMinutes()); //
            }
        }

        vacationRepo.delete(vr); //
        logger.info("VacationService: VacationRequest ID {} wurde gelöscht (admin='{}').", vacationId, adminUsername); //
        VacationRequest deletedRequest = new VacationRequest(); //
        deletedRequest.setId(vacationId); //
        return deletedRequest; //
    }


    public List<VacationRequest> getAllVacations() {
        return vacationRepo.findAll(); //
    }

    public double calculateRemainingVacationDays(String username, int year) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username)); //
        double annualVacationDays = (user.getAnnualVacationDays() != null)
                ? user.getAnnualVacationDays()
                : 25.0; //
        List<VacationRequest> vacations = vacationRepo.findByUserAndApprovedTrue(user); //
        double usedDays = 0.0; //
        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;


        for (VacationRequest vr : vacations) { //
            if (!vr.isUsesOvertime()) { //
                LocalDate startDate = vr.getStartDate(); //
                LocalDate endDate = vr.getEndDate(); //

                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) { //
                    if (date.getYear() == year) { //
                        // workScheduleService.isDayOff berücksichtigt Feiertage und Wochenenden
                        if (!workScheduleService.isDayOff(user, date)) { //
                            usedDays += vr.isHalfDay() ? 0.5 : 1.0; //
                        }
                    }
                }
            }
        }
        logger.info("VacationService: Benutzer '{}' hat {} reguläre Urlaubstage von {} im Jahr {} genutzt.",
                username, usedDays, annualVacationDays, year); //
        return annualVacationDays - usedDays; //
    }

    @Transactional
    public void deleteVacationsByUser(User user) {
        logger.info("VacationService: Lösche alle VacationRequests für Benutzer '{}'.", user.getUsername()); //
        List<VacationRequest> userVacations = vacationRepo.findByUser(user); //
        boolean userSaldoChanged = false; //
        int totalRestoredMinutes = 0; //
        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;

        for (VacationRequest vr : userVacations) { //
            if (vr.isApproved() && vr.isUsesOvertime() && (user.getIsHourly() == null || !user.getIsHourly())) { //
                int minutesToRestore; //
                if (vr.getOvertimeDeductionMinutes() != null && vr.getOvertimeDeductionMinutes() > 0) { //
                    minutesToRestore = vr.getOvertimeDeductionMinutes(); //
                } else {
                    long actualWorkDaysInVacationPeriod = 0; //
                    for (LocalDate date = vr.getStartDate(); !date.isAfter(vr.getEndDate()); date = date.plusDays(1)) { //
                        // workScheduleService.isDayOff berücksichtigt Feiertage
                        if (!workScheduleService.isDayOff(user, date)) { //
                            actualWorkDaysInVacationPeriod++; //
                        }
                    }
                    if (actualWorkDaysInVacationPeriod > 0) { //
                        int dailyMinutesValue = getDailyVacationMinutes(user); //
                        minutesToRestore = (int) Math.round(actualWorkDaysInVacationPeriod * (vr.isHalfDay() && actualWorkDaysInVacationPeriod == 1 ? 0.5 : 1.0) * dailyMinutesValue); //
                    } else {
                        minutesToRestore = 0; //
                    }
                }

                if (minutesToRestore > 0) { //
                    totalRestoredMinutes += minutesToRestore; //
                    userSaldoChanged = true; //
                    logger.info("VacationService (deleteVacationsByUser): Überstundenurlaub ID {} wird gelöscht. {} Minuten werden User '{}' gutgeschrieben.", vr.getId(), minutesToRestore, user.getUsername()); //
                }
            }
        }

        vacationRepo.deleteByUser(user); //

        if (userSaldoChanged) { //
            User freshUser = userRepo.findById(user.getId()).orElse(null); //
            if (freshUser != null) { //
                int currentBalance = freshUser.getTrackingBalanceInMinutes() != null ? freshUser.getTrackingBalanceInMinutes() : 0; //
                freshUser.setTrackingBalanceInMinutes(currentBalance + totalRestoredMinutes); //
                userRepo.save(freshUser); //
                logger.info("VacationService (deleteVacationsByUser): Saldo für User '{}' um insgesamt {} Minuten aktualisiert.", freshUser.getUsername(), totalRestoredMinutes); //
            } else {
                logger.warn("Konnte User {} nach Löschen der Urlaube nicht finden, um Saldo zu aktualisieren.", user.getUsername()); //
            }
        }
    }


    private int getDailyVacationMinutes(User user) {
        if (user == null) { //
            logger.warn("getDailyVacationMinutes called with null user."); //
            return 0; //
        }

        if (Boolean.TRUE.equals(user.getIsHourly())) { //
            double dailyHours = (user.getDailyWorkHours() != null && user.getDailyWorkHours() > 0)
                    ? user.getDailyWorkHours()
                    : 8.5; //
            int minutes = (int) Math.round(dailyHours * 60); //
            logger.debug("VacationService.getDailyVacationMinutes für {} (Stündlich): {} Minuten (Basis: {}h)", user.getUsername(), minutes, dailyHours); //
            return minutes; //
        }

        if (Boolean.TRUE.equals(user.getIsPercentage())) { //
            double dailyHoursFor100PercentBase; //
            if (user.getExpectedWorkDays() != null &&
                    user.getExpectedWorkDays() >= 1 &&
                    user.getExpectedWorkDays() <= 7) { //
                dailyHoursFor100PercentBase = BASE_FULL_TIME_WEEKLY_HOURS / user.getExpectedWorkDays(); //
                logger.debug("VacationService.getDailyVacationMinutes für {} (Prozentual {}%, {} Tage): Basis-Stunden (100%): {}h",
                        user.getUsername(), user.getWorkPercentage(), user.getExpectedWorkDays(), dailyHoursFor100PercentBase); //
            } else {
                dailyHoursFor100PercentBase = BASE_FULL_TIME_WEEKLY_HOURS / 5.0; //
                logger.warn("VacationService.getDailyVacationMinutes für {} (Prozentual {}%): Ungültige oder fehlende expectedWorkDays (Wert: {}). Verwende Fallback auf 5-Tage-Woche für 100%-Basis: {}h.",
                        user.getUsername(), user.getWorkPercentage(), user.getExpectedWorkDays(), dailyHoursFor100PercentBase); //
            }

            double workPercentageDecimal = (user.getWorkPercentage() != null) ? user.getWorkPercentage() / 100.0 : 1.0; //
            double effectiveDailyHours = dailyHoursFor100PercentBase * workPercentageDecimal; //
            int minutes = (int) Math.round(effectiveDailyHours * 60); //
            logger.debug("VacationService.getDailyVacationMinutes für {} (Prozentual {}%): Effektive Urlaubsminuten für einen Tag: {}",
                    user.getUsername(), user.getWorkPercentage(), minutes); //
            return minutes; //
        } else {
            double dailyHours = (user.getDailyWorkHours() != null && user.getDailyWorkHours() > 0)
                    ? user.getDailyWorkHours()
                    : 8.5; //
            int minutes = (int) Math.round(dailyHours * 60); //
            logger.debug("VacationService.getDailyVacationMinutes für {} (Fest): {} Minuten (Basis: {}h)", user.getUsername(), minutes, dailyHours); //
            return minutes; //
        }
    }
    // Die folgenden Methoden (getHolidays, calculateEasterSunday) wurden entfernt,
    // da die Logik nun zentral im HolidayService liegt.
}