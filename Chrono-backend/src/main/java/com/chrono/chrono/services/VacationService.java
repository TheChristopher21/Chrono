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

    @Autowired
    private ExternalNotificationService externalNotificationService;

    private boolean isConflicting(User user, LocalDate start, LocalDate end) {
        if (user == null || user.getCompany() == null) return false;
        List<VacationRequest> approved = vacationRepo
                .findByUser_Company_IdAndApprovedTrue(user.getCompany().getId());
        for (VacationRequest existing : approved) {
            if (existing.getUser().getId().equals(user.getId())) continue;
            LocalDate s = existing.getStartDate();
            LocalDate e = existing.getEndDate();
            boolean overlap = !(end.isBefore(s) || start.isAfter(e));
            if (overlap) return true;
        }
        return false;
    }


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

        // REMOVED THE LINE BELOW, AS 'targetUser' IS NOT DEFINED HERE.
        // if (isConflicting(targetUser, start, end)) {
        //     throw new IllegalArgumentException("Urlaub kollidiert mit bestehenden genehmigten Anträgen.");
        // }

        if (isConflicting(user, start, end)) { // Corrected line
            throw new IllegalArgumentException("Urlaubsantrag steht im Konflikt mit bestehenden genehmigten Anträgen.");
        }

        VacationRequest vr = new VacationRequest(); //
        vr.setUser(user); //
        vr.setStartDate(start); //
        vr.setEndDate(end); //
        vr.setDenied(false); //
        vr.setHalfDay(halfDay); //
        vr.setUsesOvertime(usesOvertime); //

        // User-submitted vacation requests should not be auto-approved.
        // An admin must explicitly approve or deny the request later.
        vr.setApproved(false);

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
        externalNotificationService.sendVacationNotification(saved, "Neuer Urlaubsantrag von " + username);
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

        externalNotificationService.sendVacationNotification(savedVr, "Urlaub für " + targetUsername + " erstellt und genehmigt");

        logger.info("VacationService: Admin '{}' hat VacationRequest ID {} für '{}' erstellt und genehmigt (usesOvertime={}, deductionMinutes={}).",
                adminUsername, savedVr.getId(), targetUsername, usesOvertime, savedVr.getOvertimeDeductionMinutes()); //
        return savedVr; //
    }

    @Transactional
    public List<VacationRequest> adminCreateCompanyVacation(String adminUsername,
                                                            LocalDate start, LocalDate end,
                                                            boolean halfDay) {
        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin user " + adminUsername + " not found"));
        Company company = admin.getCompany();
        if (company == null) {
            throw new SecurityException("Admin user " + adminUsername + " is not assigned to a company.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Das Enddatum darf nicht vor dem Startdatum liegen.");
        }
        if (halfDay && !start.isEqual(end)) {
            throw new IllegalArgumentException("Halbtags Urlaub kann nur für einen einzelnen Tag beantragt werden.");
        }
        List<User> users = userRepo.findByCompany_Id(company.getId());
        List<VacationRequest> created = new ArrayList<>();
        for (User user : users) {
            VacationRequest vr = new VacationRequest();
            vr.setUser(user);
            vr.setStartDate(start);
            vr.setEndDate(end);
            vr.setApproved(true);
            vr.setDenied(false);
            vr.setHalfDay(halfDay);
            vr.setUsesOvertime(false);
            vr.setCompanyVacation(true);
            created.add(vacationRepo.save(vr));
        }
        return created;
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
        externalNotificationService.sendVacationNotification(updated, "Urlaub von " + user.getUsername() + " genehmigt");
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
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) { //
            if (isChargeableVacationDay(user, date)) {
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

    private int restoreOvertimeForVacation(VacationRequest vr, String logContext) {
        User user = vr.getUser();
        if (user == null) {
            logger.warn("{}: VacationRequest ID {} hat keinen User. Keine Überstunden-Wiederherstellung möglich.", logContext, vr.getId());
            return 0;
        }
        if (Boolean.TRUE.equals(user.getIsHourly())) {
            logger.info("{}: User '{}' ist stundenbasiert. Keine Überstunden-Wiederherstellung erforderlich.", logContext, user.getUsername());
            return 0;
        }

        int minutesToRestore;
        if (vr.getOvertimeDeductionMinutes() != null && vr.getOvertimeDeductionMinutes() > 0) {
            minutesToRestore = vr.getOvertimeDeductionMinutes();
            logger.info("{}: Stelle {} Minuten aus gespeicherten overtimeDeductionMinutes für User '{}' wieder her (VacationRequest ID {}).",
                    logContext, minutesToRestore, user.getUsername(), vr.getId());
        } else {
            long actualWorkDaysInVacationPeriod = 0;
            for (LocalDate date = vr.getStartDate(); !date.isAfter(vr.getEndDate()); date = date.plusDays(1)) {
                if (isChargeableVacationDay(user, date)) {
                    actualWorkDaysInVacationPeriod++;
                }
            }
            if (actualWorkDaysInVacationPeriod > 0) {
                int dailyMinutesValue = getDailyVacationMinutes(user);
                minutesToRestore = (int) Math.round(actualWorkDaysInVacationPeriod * (vr.isHalfDay() && actualWorkDaysInVacationPeriod == 1 ? 0.5 : 1.0) * dailyMinutesValue);
                logger.info("{}: Stelle {} Minuten (berechnet) für User '{}' wieder her (VacationRequest ID {}, Arbeitstage: {}).",
                        logContext, minutesToRestore, user.getUsername(), vr.getId(), actualWorkDaysInVacationPeriod);
            } else {
                minutesToRestore = 0;
                logger.info("{}: Keine Arbeitstage im Zeitraum für VacationRequest ID {}. Keine Minuten für User '{}' wiederherzustellen.",
                        logContext, vr.getId(), user.getUsername());
            }
        }

        if (minutesToRestore > 0) {
            int currentBalance = user.getTrackingBalanceInMinutes() != null ? user.getTrackingBalanceInMinutes() : 0;
            user.setTrackingBalanceInMinutes(currentBalance + minutesToRestore);
            userRepo.save(user);
            logger.info("{}: {} Minuten wurden User '{}' gutgeschrieben. Neuer Saldo: {}.",
                    logContext, minutesToRestore, user.getUsername(), user.getTrackingBalanceInMinutes());
        }

        return minutesToRestore;
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
        externalNotificationService.sendVacationNotification(updated, "Urlaub von " + user.getUsername() + " abgelehnt");
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

        if (vr.isApproved() && vr.isUsesOvertime()) {
            restoreOvertimeForVacation(vr, "Lösche genehmigten Überstundenurlaub ID " + vacationId);
        }

        vacationRepo.delete(vr); //
        logger.info("VacationService: VacationRequest ID {} wurde gelöscht (admin='{}').", vacationId, adminUsername); //
        VacationRequest deletedRequest = new VacationRequest(); //
        deletedRequest.setId(vacationId); //
        return deletedRequest; //
    }

    @Transactional
    public VacationRequest adminUpdateVacation(Long vacationId,
                                               String adminUsername,
                                               LocalDate newStartDate,
                                               LocalDate newEndDate,
                                               Boolean halfDayParam,
                                               Boolean usesOvertimeParam,
                                               Integer overtimeDeductionMinutesParam,
                                               Boolean approvedParam,
                                               Boolean deniedParam) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request " + vacationId + " not found"));
        User admin = userRepo.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin user " + adminUsername + " not found"));
        User targetUser = vr.getUser();

        if (targetUser == null) {
            throw new IllegalStateException("Vacation request has no associated user.");
        }

        boolean isSuperAdmin = admin.getRoles().stream().anyMatch(role -> role.getRoleName().equals("ROLE_SUPERADMIN"));
        if (!isSuperAdmin) {
            Company adminCompany = admin.getCompany();
            Company targetCompany = targetUser.getCompany();
            if (adminCompany == null || targetCompany == null || !adminCompany.getId().equals(targetCompany.getId())) {
                throw new SecurityException("Admin und Benutzer des Urlaubsantrags gehören nicht zur selben Firma oder Firma nicht gesetzt.");
            }
        }

        LocalDate effectiveStart = newStartDate != null ? newStartDate : vr.getStartDate();
        LocalDate effectiveEnd = newEndDate != null ? newEndDate : vr.getEndDate();

        if (effectiveStart == null || effectiveEnd == null) {
            throw new IllegalArgumentException("Start- und Enddatum dürfen nicht leer sein.");
        }
        if (effectiveEnd.isBefore(effectiveStart)) {
            throw new IllegalArgumentException("Das Enddatum darf nicht vor dem Startdatum liegen.");
        }

        boolean newHalfDay = halfDayParam != null ? halfDayParam : vr.isHalfDay();
        if (newHalfDay && !effectiveStart.isEqual(effectiveEnd)) {
            throw new IllegalArgumentException("Halbtags Urlaub kann nur für einen einzelnen Tag eingetragen werden.");
        }

        boolean previousApproved = vr.isApproved();
        boolean previousUsesOvertime = vr.isUsesOvertime();
        boolean newApproved = approvedParam != null ? approvedParam : vr.isApproved();
        boolean newDenied = deniedParam != null ? deniedParam : vr.isDenied();

        if (newApproved) {
            newDenied = false;
        } else if (newDenied) {
            newApproved = false;
        }

        boolean newUsesOvertime = usesOvertimeParam != null ? usesOvertimeParam : vr.isUsesOvertime();
        if (vr.isCompanyVacation()) {
            newUsesOvertime = false;
        }

        if (previousApproved && previousUsesOvertime) {
            restoreOvertimeForVacation(vr, "Aktualisiere Urlaub ID " + vacationId);
        }

        vr.setStartDate(effectiveStart);
        vr.setEndDate(effectiveEnd);
        vr.setHalfDay(newHalfDay);
        vr.setUsesOvertime(newUsesOvertime);
        vr.setApproved(newApproved);
        vr.setDenied(newDenied);

        if (!newUsesOvertime) {
            vr.setOvertimeDeductionMinutes(null);
        } else if (Boolean.TRUE.equals(targetUser.getIsPercentage())) {
            if (overtimeDeductionMinutesParam == null || overtimeDeductionMinutesParam <= 0) {
                throw new IllegalArgumentException("Für prozentuale Mitarbeitende müssen abzuziehende Überstunden-Minuten angegeben werden.");
            }
            vr.setOvertimeDeductionMinutes(overtimeDeductionMinutesParam);
        } else {
            vr.setOvertimeDeductionMinutes(null);
        }

        VacationRequest saved = vacationRepo.save(vr);

        if (saved.isApproved() && saved.isUsesOvertime() && (targetUser.getIsHourly() == null || !targetUser.getIsHourly())) {
            applyOvertimeDeduction(saved);
            saved = vacationRepo.save(saved);
        }

        logger.info("VacationService: Urlaub ID {} wurde von Admin '{}' aktualisiert (approved={}, denied={}, usesOvertime={}).",
                vacationId, adminUsername, saved.isApproved(), saved.isDenied(), saved.isUsesOvertime());
        return saved;
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
        for (VacationRequest vr : vacations) { //
            if (!vr.isUsesOvertime() && !vr.isCompanyVacation()) { //
                LocalDate startDate = vr.getStartDate(); //
                LocalDate endDate = vr.getEndDate(); //

                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) { //
                    if (date.getYear() == year) { //
                        if (isChargeableVacationDay(user, date)) {
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
        for (VacationRequest vr : userVacations) { //
            if (vr.isApproved() && vr.isUsesOvertime() && (user.getIsHourly() == null || !user.getIsHourly())) { //
                int minutesToRestore; //
                if (vr.getOvertimeDeductionMinutes() != null && vr.getOvertimeDeductionMinutes() > 0) { //
                    minutesToRestore = vr.getOvertimeDeductionMinutes(); //
                } else {
                    long actualWorkDaysInVacationPeriod = 0; //
                    for (LocalDate date = vr.getStartDate(); !date.isAfter(vr.getEndDate()); date = date.plusDays(1)) { //
                        if (isChargeableVacationDay(user, date)) {
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

    private double calculateRequestedVacationDays(User user, LocalDate start, LocalDate end, boolean halfDay) {
        double days = 0.0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isChargeableVacationDay(user, d)) {
                days += 1.0;
            }
        }
        if (halfDay && start.equals(end)) {
            days -= 0.5;
        }
        return days;
    }

    private boolean isChargeableVacationDay(User user, LocalDate date) {
        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;
        if (holidayService.isHoliday(date, cantonAbbreviation)) {
            return false;
        }
        return !workScheduleService.isDayOff(user, date);
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
