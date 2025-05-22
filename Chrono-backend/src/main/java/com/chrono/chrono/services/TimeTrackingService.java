package com.chrono.chrono.services;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.dto.PercentagePunchResponse;
import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Zentrale Service-Klasse zur Zeiterfassung.
 * Enthält Punch-Methoden, Überstundenberechnung, AutoPunchOut-Logik
 * und die Weekly-Balance (getWeeklyBalance) für das Admin-Dashboard.
 */
@Service
public class TimeTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(TimeTrackingService.class);

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private WorkScheduleService workScheduleService;

    // 8h30 pro Tag => 510 Minuten
    public static final int FULL_DAY_MINUTES = 510;

    // Anzahl Versuche bei Deadlocks
    private static final int MAX_DEADLOCK_RETRIES = 3;


    // -------------------------------------------------------------------------
    // PRIVATE HELPER
    // -------------------------------------------------------------------------

    /** Lädt einen User oder wirft UserNotFoundException. */
    private User loadUserByUsername(String username) {
        logger.debug("loadUserByUsername(username={}) aufgerufen", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.warn("User '{}' nicht gefunden!", username);
                    return new UserNotFoundException("User not found: " + username);
                });
    }

    /** Konvertiert Status-String in PunchOrder-Zahl (1..4). */
    private int mapStatusToPunchOrder(String newStatus) {
        switch (newStatus) {
            case "WORK_START":  return 1;
            case "BREAK_START": return 2;
            case "BREAK_END":   return 3;
            case "WORK_END":    return 4;
            default:
                logger.warn("mapStatusToPunchOrder: Unbekannter Status {}", newStatus);
                return 0;
        }
    }


    // -------------------------------------------------------------------------
    // PUNCH-LOGIK
    // -------------------------------------------------------------------------

    /**
     * Direkte Punch-Aktion (WORK_START, BREAK_START, BREAK_END, WORK_END).
     * Umgeht die "Smart Punch"-Automatik.
     */
    @Transactional
    public TimeTrackingResponse handlePunch(String username, String newStatus) {
        logger.info("handlePunch aufgerufen (username={}, newStatus={})", username, newStatus);

        User user = loadUserByUsername(username);
        int order = mapStatusToPunchOrder(newStatus);

        LocalDateTime now = LocalDateTime.now();
        TimeTracking tt = createPunchInSeparateTx(user, order, now);

        logger.info("handlePunch abgeschlossen: user={}, punchOrder={}", username, order);
        return convertToResponse(tt);
    }

    /**
     * "Smart Punch": geht automatisch 1->2->3->4
     * + Anti-Doppel-Klick + orElse(0), um NoSuchElementException zu vermeiden.
     */
    @Transactional
    public TimeTrackingResponse handleSmartPunch(String username) {
        logger.info("handleSmartPunch aufgerufen (username={})", username);

        User user  = loadUserByUsername(username);
        LocalDate today = LocalDate.now();

        // Letzten Punch heute ermitteln, absteigend
        Optional<TimeTracking> lastOpt = timeTrackingRepository
                .findTopByUserAndDailyDateOrderByPunchOrderDesc(user, today);

        // Falls gar kein Punch => lastOrder=0
        int lastOrder = lastOpt.map(TimeTracking::getPunchOrder).orElse(0);
        logger.debug("handleSmartPunch: lastOrder={} (User={})", lastOrder, user.getUsername());

        // Anti-Doppel-Klick: wenn letzter Punch < 5s her
        if (lastOpt.isPresent()) {
            LocalDateTime lastTime = lastOpt.get().getStartTime();
            long seconds = Duration.between(lastTime, LocalDateTime.now()).abs().getSeconds();
            if (seconds < 5) {
                logger.info("handleSmartPunch: Doppel-Klick erkannt ({}s).", seconds);
                return convertToResponse(lastOpt.get());
            }
        }

        // Falls Tag schon beendet (#4)
        if (lastOrder == 4 && lastOpt.isPresent()) {
            logger.info("handleSmartPunch: Tag bereits beendet, gebe letzten Punch zurück.");
            return convertToResponse(lastOpt.get());
        } else if (lastOrder == 4) {
            // lastOpt leer => fallback
            logger.debug("handleSmartPunch: lastOrder=4, aber kein DS -> setze lastOrder=0");
            lastOrder = 0;
        }

        // Bestimme nextOrder  (1->2->3->4, 0 => 1)
        int nextOrder;
        switch (lastOrder) {
            case 0:
            case 4:
                nextOrder = 1; break;
            case 1:
                nextOrder = 2; break;
            case 2:
                nextOrder = 3; break;
            case 3:
                nextOrder = 4; break;
            default:
                nextOrder = 1;
                break;
        }

        LocalDateTime now = LocalDateTime.now();
        TimeTracking newTt = createPunchInSeparateTx(user, nextOrder, now);


        logger.info("handleSmartPunch: nextOrder={} angelegt (User={})", nextOrder, user.getUsername());
        return convertToResponse(newTt);
    }

    /**
     * Separate Methode (eigene Transaktion) mit "Finde oder Erzeuge" + Deadlock-Retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public TimeTracking createPunchInSeparateTx(User user, int punchOrder, LocalDateTime now) {
        logger.debug("createPunchInSeparateTx aufgerufen (user={}, punchOrder={}, now={})",
                user.getUsername(), punchOrder, now);

        for (int attempt = 1; attempt <= MAX_DEADLOCK_RETRIES; attempt++) {
            try {
                // (1) check if already exists
                Optional<TimeTracking> existing = timeTrackingRepository
                        .findFirstByUserAndDailyDateAndPunchOrder(user, now.toLocalDate(), punchOrder);

                if (existing.isPresent()) {
                    logger.debug("createPunchInSeparateTx: DS existiert schon, gebe ihn zurück");
                    return existing.get();
                }

                // (2) Neu anlegen
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
                em.clear();
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
                em.clear();
                Optional<TimeTracking> existing2 = timeTrackingRepository
                        .findFirstByUserAndDailyDateAndPunchOrder(user, now.toLocalDate(), punchOrder);
                if (existing2.isPresent()) {
                    logger.debug("createPunchInSeparateTx: DS gefunden nach DuplicateKey");
                    return existing2.get();
                }
                throw dup; // wenn immer noch nicht da, werfe
            }
        }
        throw new IllegalStateException("createPunchInSeparateTx: Unreachable, max deadlock retries exceeded");
    }

    /**
     * Hilfsmethode zum Anlegen eines PunchOrder (#4 etc.) im AutoPunchOut.
     */
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

    // -------------------------------------------------------------------------
    // AUTO-PUNCH-OUT
    // -------------------------------------------------------------------------
    @Scheduled(cron = "0 20 23 * * *", zone = "Europe/Zurich")
    @Transactional
    public void autoPunchOut() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        logger.info("autoPunchOut gestartet ({} {})", today, now.toLocalTime());

        LocalDateTime startOfDay = today.atStartOfDay();
        List<TimeTracking> openPunches =
                timeTrackingRepository.findByEndTimeIsNullAndStartTimeBetween(startOfDay, now);

        // gruppieren
        Map<User, List<TimeTracking>> byUser = openPunches.stream()
                .collect(Collectors.groupingBy(TimeTracking::getUser));

        byUser.forEach((user, punches) -> completeMissingPunchesForUser(user, punches, now));

        logger.info("✅ AutoPunchOut beendet – {}", today);
    }

    /**
     * Sorgt dafür, dass pro Tag (isPercentage => 2 Stempel, sonst 4) sauber abgeschlossen wird.
     */
    private void completeMissingPunchesForUser(User user, List<TimeTracking> openPunches, LocalDateTime punchOutTime) {
        logger.debug("completeMissingPunchesForUser aufgerufen (user={}, #openPunches={})",
                user.getUsername(), openPunches.size());

        openPunches.sort(Comparator.comparingInt(tt ->
                Optional.ofNullable(tt.getPunchOrder()).orElse(0)));
        TimeTracking last = openPunches.get(openPunches.size()-1);
        int lastOrder = Optional.ofNullable(last.getPunchOrder()).orElse(0);

        // Prüfe, ob #4 schon existiert
        Optional<TimeTracking> existing4 = timeTrackingRepository
                .findFirstByUserAndDailyDateAndPunchOrder(user, punchOutTime.toLocalDate(), 4);
        if (existing4.isPresent()) {
            logger.warn("AutoPunchOut {}: PunchOrder #4 existiert schon, skip", user.getUsername());
            return;
        }

        switch (lastOrder) {
            case 1 -> {
                // Nur WORK_START => #4 => 2 Stempel
                createPunchForUser(user, 4, punchOutTime);
                logger.info("completeMissingPunchesForUser: user={}, lastOrder=1 => #4 erzeugt => 2 Stempel", user.getUsername());
            }
            case 2 -> {
                // Pause gestartet, kein BREAK_END
                if (Boolean.TRUE.equals(user.getIsPercentage())) {
                    // rename #2 => #4
                    last.setPunchOrder(4);
                    last.setEndTime(punchOutTime);
                    last.setWorkEnd(punchOutTime.toLocalTime());
                    timeTrackingRepository.save(last);
                    logger.info("completeMissingPunchesForUser: user={}, lastOrder=2 => rename->#4 (isPercentage)", user.getUsername());
                } else {
                    // #3 + #4
                    createPunchForUser(user, 3, punchOutTime);
                    createPunchForUser(user, 4, punchOutTime);
                    logger.info("completeMissingPunchesForUser: user={}, lastOrder=2 => #3+#4 erzeugt => 4 Stempel", user.getUsername());
                }
            }
            case 3 -> {
                // Pause beendet => #4
                createPunchForUser(user, 4, punchOutTime);
                logger.info("completeMissingPunchesForUser: user={}, lastOrder=3 => #4 erzeugt => 4 Stempel", user.getUsername());
            }
            case 4 -> {
                logger.warn("completeMissingPunchesForUser: user={}, #4 existiert, skip", user.getUsername());
            }
            default -> {
                logger.info("completeMissingPunchesForUser: user={}, fallback lastOrder={}, force #4", user.getUsername(), lastOrder);
                createPunchForUser(user, 4, punchOutTime);
            }
        }
    }

    // -------------------------------------------------------------------------
    // TÄGLICHE BALANCE UM 00:05
    // -------------------------------------------------------------------------
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void bookDailyBalances() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        logger.info("bookDailyBalances gestartet für {}", yesterday);

        userRepository.findAll().forEach(u -> {
            if (Boolean.TRUE.equals(u.getIsPercentage())) {
                return; // Prozentbasierte User werden wöchentlich abgerechnet
            }
            int delta = computeDailyWorkDifference(u, yesterday.toString());
            int oldBal = Optional.ofNullable(u.getTrackingBalanceInMinutes()).orElse(0);
            u.setTrackingBalanceInMinutes(oldBal + delta);
        });

        logger.info("✅ bookDailyBalances abgeschlossen für {}", yesterday);
    }

    // -------------------------------------------------------------------------
    // BERECHNUNG: Überstunden (1 Pause)
    // -------------------------------------------------------------------------
    public int computeDailyWorkDifference(User user, String isoDate) {
        logger.debug("computeDailyWorkDifference: user={}, isoDate={}", user.getUsername(), isoDate);
        LocalDate day = LocalDate.parse(isoDate);

        // Alle Stempel des Tages chronologisch holen
        LocalDateTime startOfDay = day.atStartOfDay();
        LocalDateTime endOfDay   = day.plusDays(1).atStartOfDay();
        List<TimeTracking> list = timeTrackingRepository
                .findByUserAndStartTimeBetweenOrderByPunchOrderAsc(user, startOfDay, endOfDay);

        // container
        LocalDateTime ws = null; // #1
        LocalDateTime bs = null; // #2
        LocalDateTime be = null; // #3
        LocalDateTime we = null; // #4

        for (TimeTracking t : list) {
            int po = Optional.ofNullable(t.getPunchOrder()).orElse(0);
            switch (po) {
                case 1 -> ws = toDateTime(t);
                case 2 -> bs = toDateTime(t);
                case 3 -> be = toDateTime(t);
                case 4 -> we = toDateTime(t);
            }
        }

        int worked = 0;
        if (ws != null && we != null) {
            worked = (int) ChronoUnit.MINUTES.between(ws, we);
            if (bs != null && be != null) {
                worked -= (int) ChronoUnit.MINUTES.between(bs, be);
            }
        }

        int expected;
        if (Boolean.TRUE.equals(user.getIsHourly())) {
            expected = 0;
        } else {
            expected = workScheduleService.computeExpectedWorkMinutes(user, day);
            // 42h-/8h-Regel: pro Tag maximal 8h ansetzen
            expected = Math.min(expected, 8 * 60);
            if (Boolean.TRUE.equals(user.getIsPercentage())) {
                int perc = Optional.ofNullable(user.getWorkPercentage()).orElse(100);
                expected = (int) Math.round(expected * (perc / 100.0));
            }
        }
        int diff = worked - expected;
        logger.debug("computeDailyWorkDifference: user={}, worked={}, expected={}, diff={}", user.getUsername(), worked, expected, diff);
        return diff;
    }

    private LocalDateTime toDateTime(TimeTracking t) {
        if (t.getStartTime() != null) return t.getStartTime();
        if (t.getEndTime()   != null) return t.getEndTime();
        return null;
    }

    // -------------------------------------------------------------------------
    // WÖCHENTLICHE BALANCE FÜR PERCENTAGE-USER
    // -------------------------------------------------------------------------
    @Scheduled(cron = "0 10 0 * * MON")
    @Transactional
    public void bookWeeklyPercentageBalances() {
        LocalDate mondayLastWeek = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1);
        logger.info("bookWeeklyPercentageBalances gestartet für Woche {}", mondayLastWeek);

        userRepository.findAll().forEach(u -> {
            if (Boolean.TRUE.equals(u.getIsPercentage())) {
                int delta = computeWeeklyWorkDifference(u, mondayLastWeek);
                int oldBal = Optional.ofNullable(u.getTrackingBalanceInMinutes()).orElse(0);
                u.setTrackingBalanceInMinutes(oldBal + delta);
            }
        });

        logger.info("✅ bookWeeklyPercentageBalances abgeschlossen für Woche {}", mondayLastWeek);
    }

    private int computeWeeklyWorkDifference(User user, LocalDate monday) {
        int weeklyDiff = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            weeklyDiff += computeDailyWorkDifference(user, day.toString());
        }

        logger.debug("computeWeeklyWorkDifference: user={}, monday={}, diff={} min",
                user.getUsername(), monday, weeklyDiff);
        return weeklyDiff;
    }

    // -------------------------------------------------------------------------
    //  handlePercentagePunch
    // -------------------------------------------------------------------------
    public PercentagePunchResponse handlePercentagePunch(String username, double percentage) {
        logger.info("handlePercentagePunch: user={}, percentage={}", username, percentage);
        int expected = (int) Math.round(FULL_DAY_MINUTES * (percentage / 100.0));
        User user = loadUserByUsername(username);

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay   = today.plusDays(1).atStartOfDay();

        // gearbeitete Zeit
        int worked = timeTrackingRepository
                .findByUserAndStartTimeBetween(user, startOfDay, endOfDay)
                .stream()
                .filter(tt -> tt.getStartTime() != null && tt.getEndTime() != null)
                .mapToInt(tt -> (int) ChronoUnit.MINUTES.between(tt.getStartTime(), tt.getEndTime()))
                .sum();

        int diff = worked - expected;
        String msg = (diff >= 0)
                ? "Überstunden: " + diff + " min"
                : "Fehlstunden: " + (-diff) + " min";

        logger.info("handlePercentagePunch: worked={}, expected={}, diff={}", worked, expected, diff);
        return new PercentagePunchResponse(username, worked, expected, diff, msg);
    }

    // -------------------------------------------------------------------------
    // HISTORY, NOTES, REPORTS, etc.
    // -------------------------------------------------------------------------
    public List<TimeTrackingResponse> getUserHistory(String username) {
        logger.info("getUserHistory: username={}", username);
        User user = loadUserByUsername(username);

        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByStartTimeDesc(user);
        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Transactional
    public TimeTrackingResponse updateDailyNote(String username, String date, String note) {
        logger.info("updateDailyNote: user={}, date={}, note='{}'", username, date, note);

        User user = loadUserByUsername(username);
        if (!user.isHourly()) {
            logger.warn("updateDailyNote: user {} ist kein stundenbasierter Mitarbeiter!", username);
            throw new RuntimeException("Daily note is only available for hourly employees.");
        }
        LocalDate localDate = LocalDate.parse(date);
        List<TimeTracking> noteEntries = timeTrackingRepository.findDailyNoteByUserAndDate(user, localDate);

        TimeTracking noteEntry;
        if (!noteEntries.isEmpty()) {
            noteEntry = noteEntries.get(0);
            noteEntry.setDailyNote(note);
        } else {
            noteEntry = new TimeTracking();
            noteEntry.setUser(user);
            noteEntry.setPunchOrder(0);
            noteEntry.setStartTime(localDate.atStartOfDay());
            noteEntry.setEndTime(localDate.atStartOfDay());
            noteEntry.setDailyNote(note);
            noteEntry.setCorrected(true);
        }
        noteEntry.setDailyDate(localDate);
        timeTrackingRepository.save(noteEntry);

        logger.info("updateDailyNote: Eintrag gespeichert -> dailyNote='{}'", note);
        return convertToResponse(noteEntry);
    }

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

        // 1) Alte Einträge löschen
        int deleted = timeTrackingRepository.deleteByUserAndDailyDate(targetUser, parsedDate);
        logger.info("updateDayTimeEntries: {} alte Einträge für {} gelöscht.", deleted, date);

        // 2) Neu anlegen: #1, #2, #3, #4
        // #1
        TimeTracking ts1 = new TimeTracking();
        ts1.setUser(targetUser);
        ts1.setPunchOrder(1);
        ts1.setDailyDate(parsedDate);
        ts1.setStartTime(newWorkStartDT);
        ts1.setWorkStart(newWorkStart);
        ts1.setCorrected(true);
        timeTrackingRepository.save(ts1);

        if (hasBreak) {
            // #2
            TimeTracking ts2 = new TimeTracking();
            ts2.setUser(targetUser);
            ts2.setPunchOrder(2);
            ts2.setDailyDate(parsedDate);
            ts2.setStartTime(newBreakStartDT);
            ts2.setBreakStart(newBreakStart);
            ts2.setCorrected(true);
            timeTrackingRepository.save(ts2);

            // #3
            TimeTracking ts3 = new TimeTracking();
            ts3.setUser(targetUser);
            ts3.setPunchOrder(3);
            ts3.setDailyDate(parsedDate);
            ts3.setStartTime(newBreakEndDT);
            ts3.setBreakEnd(newBreakEnd);
            ts3.setCorrected(true);
            timeTrackingRepository.save(ts3);
        }

        // #4
        TimeTracking ts4 = new TimeTracking();
        ts4.setUser(targetUser);
        ts4.setPunchOrder(4);
        ts4.setDailyDate(parsedDate);
        ts4.setStartTime(newWorkEndDT);
        ts4.setEndTime(newWorkEndDT);
        ts4.setWorkEnd(newWorkEnd);
        ts4.setCorrected(true);
        timeTrackingRepository.save(ts4);

        // 3) Balance neu berechnen
        try {
            List<String> trackedDates =
                    timeTrackingRepository.findAllTrackedDateStringsByUser(targetUser.getId());

            int newBalance = 0;
            for (String dStr : trackedDates) {
                newBalance += computeDailyWorkDifference(targetUser, dStr);
            }
            targetUser.setTrackingBalanceInMinutes(newBalance);
            userRepository.save(targetUser);

            logger.info("✅ updateDayTimeEntries: Balance für {} neu berechnet: {} min", targetUsername, newBalance);
        } catch (Exception ex) {
            logger.warn("⚠️ updateDayTimeEntries: Fehler bei Balance-Refresh: {}", ex.getMessage());
        }

        return "Day entries updated successfully";
    }

    /**
     * Erzeugt einen Tagesbericht (Liste TimeReportDTO) im Datumsbereich.
     */
    public List<TimeReportDTO> getReport(String username, String startDateStr, String endDateStr) {
        logger.info("getReport: user={}, start={}, end={}", username, startDateStr, endDateStr);

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate   = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime   = endDate.plusDays(1).atStartOfDay();

        User user = loadUserByUsername(username);

        List<TimeTracking> entries = timeTrackingRepository
                .findByUserAndStartTimeBetween(user, startDateTime, endDateTime);

        // Gruppieren nach Tag
        Map<LocalDate, List<TimeTracking>> grouped =
                entries.stream()
                        .collect(Collectors.groupingBy(e -> e.getStartTime().toLocalDate()));

        List<TimeReportDTO> report = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d.M.yyyy", Locale.GERMAN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // Sortierte Liste der Tage
        List<LocalDate> sortedDates = new ArrayList<>(grouped.keySet());
        Collections.sort(sortedDates);

        for (LocalDate date : sortedDates) {
            List<TimeTracking> dayEntries = grouped.get(date);
            dayEntries.sort(Comparator.comparingInt(
                    e -> e.getPunchOrder() != null ? e.getPunchOrder() : 0
            ));

            String workStart = "-";
            String breakStart = "-";
            String breakEnd = "-";
            String workEnd = "-";
            String dailyNote = "";

            for (TimeTracking e : dayEntries) {
                int po = (e.getPunchOrder() != null) ? e.getPunchOrder() : 0;
                if (po == 1 && workStart.equals("-")) {
                    workStart = (e.getWorkStart() != null)
                            ? e.getWorkStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (po == 2 && breakStart.equals("-")) {
                    breakStart = (e.getBreakStart() != null)
                            ? e.getBreakStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (po == 3 && breakEnd.equals("-")) {
                    breakEnd = (e.getBreakEnd() != null)
                            ? e.getBreakEnd().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (po == 4 && workEnd.equals("-")) {
                    if (e.getWorkEnd() != null) {
                        workEnd = e.getWorkEnd().format(timeFormatter);
                    } else if (e.getEndTime() != null) {
                        workEnd = e.getEndTime().format(timeFormatter);
                    }
                }
                if (e.getDailyNote() != null && !e.getDailyNote().isEmpty() && dailyNote.isEmpty()) {
                    dailyNote = e.getDailyNote();
                }
            }
            String formattedDate = date.format(dateFormatter);
            report.add(new TimeReportDTO(
                    user.getUsername(),
                    formattedDate,
                    workStart,
                    breakStart,
                    breakEnd,
                    workEnd,
                    dailyNote
            ));
        }
        logger.info("getReport: user={}, #Tage={}", username, report.size());
        return report;
    }

    // -------------------------------------------------------------------------
    //  HELFER ADMIN
    // -------------------------------------------------------------------------
    /**
     * Ergänzend: hole TimeTracking-Einträge für user in Zeitspanne
     */
    public List<TimeTracking> getTimeTrackingEntriesForUserAndDate(User user, LocalDateTime start, LocalDateTime end) {
        logger.debug("getTimeTrackingEntriesForUserAndDate: user={}, start={}, end={}",
                user.getUsername(), start, end);
        return timeTrackingRepository.findByUserAndStartTimeBetween(user, start, end);
    }

    /**
     * Hole alle TimeTracks + user => DTO.
     */
    public List<AdminTimeTrackDTO> getAllTimeTracksWithUser() {
        logger.info("getAllTimeTracksWithUser aufgerufen");
        List<TimeTracking> all = timeTrackingRepository.findAllWithUser();
        return all.stream()
                .map(AdminTimeTrackDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Admin kann einen DS direkt editieren (Start/End).
     */
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

    // -------------------------------------------------------------------------
    // PASSWORTCHECK ADMIN + USER
    // -------------------------------------------------------------------------
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

            // Selbst-Bearbeitung?
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
                // Fremd-Bearbeitung
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

    // -------------------------------------------------------------------------
    // KONVERTIERUNG -> DTO
    // -------------------------------------------------------------------------
    private TimeTrackingResponse convertToResponse(TimeTracking tt) {
        int pOrder = (tt.getPunchOrder() != null) ? tt.getPunchOrder() : 0;

        String label;
        switch (pOrder) {
            case 1: label = "Work Start";  break;
            case 2: label = "Break Start"; break;
            case 3: label = "Break End";   break;
            case 4: label = "Work End";    break;
            case 0: label = "Daily Note";  break;
            default: label = "Unknown";    break;
        }

        return new TimeTrackingResponse(
                tt.getId(),
                tt.getStartTime(),
                tt.getEndTime(),
                tt.isCorrected(),
                label,
                pOrder,
                tt.getDailyNote()
        );
    }


    // -------------------------------------------------------------------------
    //  NEU: getWeeklyBalance (für AdminTimeTrackingController)
    // -------------------------------------------------------------------------
    /**
     * Gibt eine einfache Summierung aller gearbeiteten Minuten (Mo–So)
     * ohne ausführliche Pausenberechnung. Wenn du die Pausenabzüge
     * exakt möchtest, müsstest du pro Tag computeDailyWorkDifference aufrufen.
     */
    public int getWeeklyBalance(User user, LocalDate monday) {
        logger.debug("getWeeklyBalance: user={}, monday={}", user.getUsername(), monday);
        LocalDate sunday = monday.plusDays(6);

        // Summiere "raw" alle (Start-EndTime)
        List<TimeTracking> entries = timeTrackingRepository.findByUserAndStartTimeBetween(
                user,
                monday.atStartOfDay(),
                sunday.plusDays(1).atStartOfDay()
        );

        // Summiere gearbeitete Zeit
        int total = entries.stream()
                .filter(tt -> tt.getStartTime() != null && tt.getEndTime() != null)
                .mapToInt(tt -> (int) ChronoUnit.MINUTES.between(tt.getStartTime(), tt.getEndTime()))
                .sum();

        logger.debug("getWeeklyBalance: user={}, monday={}, total={} min", user.getUsername(), monday, total);
        return total;
    }
}
