package com.chrono.chrono.services;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service with admin specific time tracking operations.
 */
@Service
public class AdminTimeTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(AdminTimeTrackingService.class);

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TimeTrackingService timeTrackingService;

    @Transactional
    public String updateDayTimeEntries(String targetUsername,
                                       String date,
                                       String workStartStr,
                                       String breakStartStr,
                                       String breakEndStr,
                                       String workEndStr,
                                       String adminUsername,
                                       String adminPassword,
                                       String userPassword) {
        logger.info("updateDayTimeEntries: target={}, date={}", targetUsername, date);

        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        checkAdminAndUserPasswords(targetUser, adminUsername, adminPassword, userPassword);

        LocalDate parsedDate = LocalDate.parse(date);
        LocalTime newWorkStart = LocalTime.parse(workStartStr);
        LocalTime newWorkEnd   = LocalTime.parse(workEndStr);

        boolean hasBreak = !(breakStartStr.equals("00:00") && breakEndStr.equals("00:00"));
        LocalTime newBreakStart = hasBreak ? LocalTime.parse(breakStartStr) : null;
        LocalTime newBreakEnd   = hasBreak ? LocalTime.parse(breakEndStr)   : null;

        LocalDateTime newWorkStartDT  = parsedDate.atTime(newWorkStart);
        LocalDateTime newWorkEndDT    = parsedDate.atTime(newWorkEnd);
        LocalDateTime newBreakStartDT = hasBreak ? parsedDate.atTime(newBreakStart) : null;
        LocalDateTime newBreakEndDT   = hasBreak ? parsedDate.atTime(newBreakEnd)   : null;

        int deleted = timeTrackingRepository.deleteByUserAndDailyDate(targetUser, parsedDate);
        logger.info("updateDayTimeEntries: {} alte Einträge für {} gelöscht.", deleted, date);

        TimeTracking ts1 = new TimeTracking();
        ts1.setUser(targetUser);
        ts1.setPunchOrder(1);
        ts1.setDailyDate(parsedDate);
        ts1.setStartTime(newWorkStartDT);
        ts1.setWorkStart(newWorkStart);
        ts1.setCorrected(true);
        timeTrackingRepository.save(ts1);

        if (hasBreak) {
            TimeTracking ts2 = new TimeTracking();
            ts2.setUser(targetUser);
            ts2.setPunchOrder(2);
            ts2.setDailyDate(parsedDate);
            ts2.setStartTime(newBreakStartDT);
            ts2.setBreakStart(newBreakStart);
            ts2.setCorrected(true);
            timeTrackingRepository.save(ts2);

            TimeTracking ts3 = new TimeTracking();
            ts3.setUser(targetUser);
            ts3.setPunchOrder(3);
            ts3.setDailyDate(parsedDate);
            ts3.setStartTime(newBreakEndDT);
            ts3.setBreakEnd(newBreakEnd);
            ts3.setCorrected(true);
            timeTrackingRepository.save(ts3);
        }

        TimeTracking ts4 = new TimeTracking();
        ts4.setUser(targetUser);
        ts4.setPunchOrder(4);
        ts4.setDailyDate(parsedDate);
        ts4.setStartTime(newWorkEndDT);
        ts4.setEndTime(newWorkEndDT);
        ts4.setWorkEnd(newWorkEnd);
        ts4.setCorrected(true);
        timeTrackingRepository.save(ts4);

        try {
            List<String> trackedDates =
                    timeTrackingRepository.findAllTrackedDateStringsByUser(targetUser.getId());

            int newBalance = 0;
            for (String dStr : trackedDates) {
                newBalance += timeTrackingService.computeDailyWorkDifference(targetUser, dStr);
            }
            targetUser.setTrackingBalanceInMinutes(newBalance);
            userRepository.save(targetUser);

            logger.info("✅ updateDayTimeEntries: Balance für {} neu berechnet: {} min", targetUsername, newBalance);
        } catch (Exception ex) {
            logger.warn("⚠️ updateDayTimeEntries: Fehler bei Balance-Refresh: {}", ex.getMessage());
        }

        return "Day entries updated successfully";
    }

    public List<TimeTracking> getTimeTrackingEntriesForUserAndDate(User user, LocalDateTime start, LocalDateTime end) {
        logger.debug("getTimeTrackingEntriesForUserAndDate: user={}, start={}, end={}",
                user.getUsername(), start, end);
        return timeTrackingRepository.findByUserAndStartTimeBetween(user, start, end);
    }

    public List<AdminTimeTrackDTO> getAllTimeTracksWithUser() {
        logger.info("getAllTimeTracksWithUser aufgerufen");
        List<TimeTracking> all = timeTrackingRepository.findAllWithUser();
        return all.stream()
                .map(AdminTimeTrackDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminTimeTrackDTO updateTimeTrackEntry(
            Long id,
            LocalDateTime newStart,
            LocalDateTime newEnd,
            String userPassword,
            String adminUsername,
            String adminPassword
    ) {
        logger.info("updateTimeTrackEntry: id={}, newStart={}, newEnd={}", id, newStart, newEnd);
        TimeTracking tt = timeTrackingRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("TimeTracking ID {} nicht gefunden", id);
                    return new RuntimeException("Time tracking entry not found");
                });
        User targetUser = tt.getUser();

        checkAdminAndUserPasswords(targetUser, adminUsername, adminPassword, userPassword);

        tt.setStartTime(newStart);
        tt.setEndTime(newEnd);
        TimeTracking updated = timeTrackingRepository.save(tt);

        logger.info("updateTimeTrackEntry: ID={} aktualisiert (start={}, end={})",
                updated.getId(), updated.getStartTime(), updated.getEndTime());

        return new AdminTimeTrackDTO(
                updated.getId(),
                updated.getUser().getUsername(),
                updated.getStartTime(),
                updated.getEndTime(),
                updated.isCorrected(),
                updated.getPunchOrder() != null ? updated.getPunchOrder() : 0,
                updated.getUser().getColor()
        );
    }

    private void checkAdminAndUserPasswords(User targetUser,
                                            String adminUsername,
                                            String adminPassword,
                                            String userPassword) {
        if (adminUsername != null && !adminUsername.trim().isEmpty()) {
            logger.debug("checkAdminAndUserPasswords: adminUsername={}, targetUser={}", adminUsername, targetUser.getUsername());

            User adminUser = userRepository.findByUsername(adminUsername)
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            String storedAdminPwd = adminUser.getAdminPassword();
            if (storedAdminPwd == null || storedAdminPwd.trim().isEmpty()) {
                storedAdminPwd = adminUser.getPassword();
                logger.info("checkAdminAndUserPasswords: Kein separates Admin-Passwort, verwende Login-Passwort.");
            }

            if (targetUser.getUsername().equals(adminUsername)) {
                if (!adminPassword.equals(userPassword)) {
                    logger.warn("checkAdminAndUserPasswords: Self-Edit, adminPassword != userPassword");
                    throw new RuntimeException("For self-edit, admin and user passwords must match");
                }
                boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
                boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
                if (!adminPwdMatch) {
                    logger.warn("checkAdminAndUserPasswords: admin password falsch");
                    throw new RuntimeException("Invalid admin password");
                }
                if (!userPwdMatch) {
                    logger.warn("checkAdminAndUserPasswords: user password falsch (self-edit)");
                    throw new RuntimeException("Invalid user password");
                }
            } else {
                boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
                boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
                if (!adminPwdMatch) {
                    logger.warn("checkAdminAndUserPasswords: admin password falsch");
                    throw new RuntimeException("Invalid admin password");
                }
                if (!userPwdMatch) {
                    logger.warn("checkAdminAndUserPasswords: user password falsch (Fremdedit)");
                    throw new RuntimeException("Invalid user password");
                }
            }
        }
    }
}
