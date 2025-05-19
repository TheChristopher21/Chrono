package com.chrono.chrono.services;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracted logic for the automatic punch-out scheduled task.
 */
@Service
public class AutoPunchService {

    private static final Logger logger = LoggerFactory.getLogger(AutoPunchService.class);

    private static final int MAX_DEADLOCK_RETRIES = 3;

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    @PersistenceContext
    private EntityManager em;

    // ---------------------------------------------------------------------
    // SCHEDULED PUNCH OUT
    // ---------------------------------------------------------------------
    @Scheduled(cron = "0 20 23 * * *", zone = "Europe/Zurich")
    @Transactional
    public void autoPunchOut() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        logger.info("autoPunchOut gestartet ({} {})", today, now.toLocalTime());

        LocalDateTime startOfDay = today.atStartOfDay();
        List<TimeTracking> openPunches =
                timeTrackingRepository.findByEndTimeIsNullAndStartTimeBetween(startOfDay, now);

        Map<User, List<TimeTracking>> byUser = openPunches.stream()
                .collect(Collectors.groupingBy(TimeTracking::getUser));

        byUser.forEach((user, punches) -> completeMissingPunchesForUser(user, punches, now));

        logger.info("✅ AutoPunchOut beendet – {}", today);
    }

    /**
     * Ensures each day is properly closed with punch order 4.
     */
    private void completeMissingPunchesForUser(User user, List<TimeTracking> openPunches, LocalDateTime punchOutTime) {
        logger.debug("completeMissingPunchesForUser aufgerufen (user={}, #openPunches={})",
                user.getUsername(), openPunches.size());

        openPunches.sort(Comparator.comparingInt(tt ->
                Optional.ofNullable(tt.getPunchOrder()).orElse(0)));
        TimeTracking last = openPunches.get(openPunches.size()-1);
        int lastOrder = Optional.ofNullable(last.getPunchOrder()).orElse(0);

        Optional<TimeTracking> existing4 = timeTrackingRepository
                .findFirstByUserAndDailyDateAndPunchOrder(user, punchOutTime.toLocalDate(), 4);
        if (existing4.isPresent()) {
            logger.warn("AutoPunchOut {}: PunchOrder #4 existiert schon, skip", user.getUsername());
            return;
        }

        switch (lastOrder) {
            case 1 -> {
                createPunchForUser(user, 4, punchOutTime);
                logger.info("completeMissingPunchesForUser: user={}, lastOrder=1 => #4 erzeugt => 2 Stempel", user.getUsername());
            }
            case 2 -> {
                if (Boolean.TRUE.equals(user.getIsPercentage())) {
                    last.setPunchOrder(4);
                    last.setEndTime(punchOutTime);
                    last.setWorkEnd(punchOutTime.toLocalTime());
                    timeTrackingRepository.save(last);
                    logger.info("completeMissingPunchesForUser: user={}, lastOrder=2 => rename->#4 (isPercentage)", user.getUsername());
                } else {
                    createPunchForUser(user, 3, punchOutTime);
                    createPunchForUser(user, 4, punchOutTime);
                    logger.info("completeMissingPunchesForUser: user={}, lastOrder=2 => #3+#4 erzeugt => 4 Stempel", user.getUsername());
                }
            }
            case 3 -> {
                createPunchForUser(user, 4, punchOutTime);
                logger.info("completeMissingPunchesForUser: user={}, lastOrder=3 => #4 erzeugt => 4 Stempel", user.getUsername());
            }
            case 4 -> logger.warn("completeMissingPunchesForUser: user={}, #4 existiert, skip", user.getUsername());
            default -> {
                logger.info("completeMissingPunchesForUser: user={}, fallback lastOrder={}, force #4", user.getUsername(), lastOrder);
                createPunchForUser(user, 4, punchOutTime);
            }
        }
    }

    // ---------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public TimeTracking createPunchInSeparateTx(User user, int punchOrder, LocalDateTime now) {
        logger.debug("createPunchInSeparateTx aufgerufen (user={}, punchOrder={}, now={})",
                user.getUsername(), punchOrder, now);

        for (int attempt = 1; attempt <= MAX_DEADLOCK_RETRIES; attempt++) {
            try {
                Optional<TimeTracking> existing = timeTrackingRepository
                        .findFirstByUserAndDailyDateAndPunchOrder(user, now.toLocalDate(), punchOrder);

                if (existing.isPresent()) {
                    logger.debug("createPunchInSeparateTx: DS existiert schon, gebe ihn zurück");
                    return existing.get();
                }

                TimeTracking tt = new TimeTracking();
                tt.setUser(user);
                tt.setPunchOrder(punchOrder);
                tt.setDailyDate(now.toLocalDate());
                tt.setStartTime(now);

                if (punchOrder == 4) {
                    tt.setEndTime(now);
                    tt.setWorkEnd(now.toLocalTime());
                }
                TimeTracking saved = timeTrackingRepository.save(tt);
                logger.debug("createPunchInSeparateTx: DS neu angelegt (PunchOrder={})", punchOrder);
                return saved;

            } catch (CannotAcquireLockException | DeadlockLoserDataAccessException ex) {
                logger.warn("createPunchInSeparateTx: Deadlock (#{}), retrying...", attempt);
                if (attempt == MAX_DEADLOCK_RETRIES) {
                    logger.error("createPunchInSeparateTx: Deadlock final, werfe Exception");
                    throw ex;
                }
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } catch (DataIntegrityViolationException dup) {
                logger.warn("createPunchInSeparateTx: DuplicateKey -> fetch existing again");
                Optional<TimeTracking> existing2 = timeTrackingRepository
                        .findFirstByUserAndDailyDateAndPunchOrder(user, now.toLocalDate(), punchOrder);
                if (existing2.isPresent()) {
                    logger.debug("createPunchInSeparateTx: DS gefunden nach DuplicateKey");
                    return existing2.get();
                }
                throw dup;
            }
        }
        throw new IllegalStateException("createPunchInSeparateTx: Unreachable, max deadlock retries exceeded");
    }

    private TimeTracking createPunchForUser(User user, int punchOrder, LocalDateTime stampTime) {
        logger.debug("createPunchForUser: user={}, punchOrder={}, stampTime={}", user.getUsername(), punchOrder, stampTime);

        TimeTracking newTt = createPunchInSeparateTx(user, punchOrder, stampTime);
        if (punchOrder == 4) {
            newTt.setEndTime(stampTime);
            newTt.setWorkEnd(stampTime.toLocalTime());
            timeTrackingRepository.save(newTt);
            logger.debug("createPunchForUser: #4 => EndTime gesetzt");
        }
        return newTt;
    }
}
