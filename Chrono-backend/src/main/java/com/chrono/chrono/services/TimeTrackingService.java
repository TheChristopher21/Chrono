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
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimeTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(TimeTrackingService.class);
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TimeTrackingService.class);

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext          // ➊  für Session-Reset nach Duplicate-Key
    private EntityManager em;

    public static final int FULL_DAY_MINUTES = 510;   // 8 h 30 min (42,5-h-Woche)

    private static final int MAX_DEADLOCK_RETRIES = 3;


    // -------------------------------------------------------------------------
    // PRIVATE HELPER
    // -------------------------------------------------------------------------
    private User loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private int mapStatusToPunchOrder(String newStatus) {
        switch (newStatus) {
            case "WORK_START":  return 1;
            case "BREAK_START": return 2;
            case "BREAK_END":   return 3;
            case "WORK_END":    return 4;
            default:            return 0;
        }
    }

    private boolean isTransitionAllowed(Integer lastPunchOrder, String newStatus) {
        logger.info("isTransitionAllowed: lastPunchOrder={}, newStatus={}", lastPunchOrder, newStatus);
        switch (newStatus) {
            case "WORK_START":
                // Erlaubt, wenn letzter PunchOrder=4 oder null
                return (lastPunchOrder == null || lastPunchOrder == 4);
            case "BREAK_START":
                // Nur wenn letzter PunchOrder=1
                return (lastPunchOrder != null && lastPunchOrder == 1);
            case "BREAK_END":
                // Nur wenn letzter PunchOrder=2
                return (lastPunchOrder != null && lastPunchOrder == 2);
            case "WORK_END":
                // Nur wenn letzter PunchOrder=1 oder 3
                return (lastPunchOrder != null && (lastPunchOrder == 1 || lastPunchOrder == 3));
            default:
                logger.warn("Unbekannter Status: {}", newStatus);
                return false;
        }
    }

    // -------------------------------------------------------------------------
    // PUNCH-LOGIK
    // -------------------------------------------------------------------------
    /**
     * Direkte Punch-Aktion (WORK_START, BREAK_START, BREAK_END, WORK_END).
     */
    /* ----------------------------------------------------------------------
     *  3)  handlePunch  – nutzt jetzt createPunchInSeparateTx()
     * --------------------------------------------------------------------*/
    @Transactional
    public TimeTrackingResponse handlePunch(String username, String status) {

        User user = loadUserByUsername(username);
        LocalDateTime now = LocalDateTime.now();
        int nextOrder     = mapStatusToPunchOrder(status);

        TimeTracking tt   = createPunchInSeparateTx(user, nextOrder, now);
        return convertToResponse(tt);
    }

    /* ----------------------------------------------------------------------
     *  4)  handleSmartPunch  – 409 (CONFLICT) statt 500
     * --------------------------------------------------------------------*/
    @Transactional
    public TimeTrackingResponse handleSmartPunch(String username) {
        User user = loadUserByUsername(username);
        LocalDate today = LocalDate.now();

        // ----- [ALT] Blockiert weitere Stempelungen nach WORK_END (PunchOrder=4) -----
        // boolean finishedToday = timeTrackingRepository
        //     .findTopByUserAndDailyDateOrderByPunchOrderDesc(user, today)
        //     .map(TimeTracking::getPunchOrder)
        //     .orElse(4) == 4;
        // if (finishedToday) {
        //     throw new ResponseStatusException(HttpStatus.CONFLICT, "Cycle already finished today");
        // }

        // Letzter Punch-Order heute aus DB
        int last = timeTrackingRepository
                .findTopByUserAndDailyDateOrderByPunchOrderDesc(user, today)
                .map(TimeTracking::getPunchOrder)
                .orElse(4);

        // Entscheidet „smart“ den nächsten Status
        String nextStatus = switch (last) {
            case 4 -> "WORK_START";   // wenn letztes =4, dann beginnt man erneut
            case 1 -> "BREAK_START";
            case 2 -> "BREAK_END";
            case 3 -> "WORK_END";
            default -> "WORK_START";
        };

        // Ruft intern handlePunch(username, nextStatus) auf
        TimeTrackingResponse resp = handlePunch(username, nextStatus);

        // Falls jetzt WORK_END erzeugt wurde -> Tagesbilanz neu berechnen
        if ("WORK_END".equals(nextStatus)) {
            int delta = computeDailyWorkDifference(user, today.toString());
            user.setTrackingBalanceInMinutes(
                    Optional.ofNullable(user.getTrackingBalanceInMinutes()).orElse(0) + delta
            );
            userRepository.save(user);
        }

        return resp;
    }



    /**
     * Separate Methode (eigene Transaktion), die Punch-Insert ausführt.
     * Falls dabei ein UNIQUE-Constraint verletzt wird (DuplicateKey),
     * holen wir uns den bereits existierenden Datensatz zurück.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation   = Isolation.SERIALIZABLE,
            noRollbackFor = {DataIntegrityViolationException.class})
    public TimeTracking createPunchInSeparateTx(User user,
                                                int punchOrder,
                                                LocalDateTime now) {

        for (int attempt = 1; attempt <= MAX_DEADLOCK_RETRIES; attempt++) {
            try {
                /* 1) Exists-Check unter Pessimistic Lock  */
                Optional<TimeTracking> existing =
                        timeTrackingRepository.findFirstByUserAndDailyDateAndPunchOrder(
                                user, now.toLocalDate(), punchOrder);
                if (existing.isPresent()) return existing.get();

                /* 2) Neuer Datensatz                             */
                TimeTracking tt = new TimeTracking();
                tt.setUser(user);
                tt.setPunchOrder(punchOrder);
                tt.setDailyDate(now.toLocalDate());
                tt.setStartTime(now);

                if (punchOrder == 4) {                 // WORK_END sofort schliessen
                    tt.setWorkEnd(now.toLocalTime());
                    tt.setEndTime(now);
                }
                return timeTrackingRepository.save(tt);

            } catch (CannotAcquireLockException | DeadlockLoserDataAccessException ex) {
                log.warn("Dead-lock (#{} / {}) – retry …", attempt, MAX_DEADLOCK_RETRIES);
                em.clear();                       // Session invalid – aufräumen
                if (attempt == MAX_DEADLOCK_RETRIES) throw ex;   // letzter Versuch
                try { Thread.sleep(50L * attempt); } catch (InterruptedException ignored) {}
            } catch (DataIntegrityViolationException dup) {
                em.clear();
                return timeTrackingRepository
                        .findFirstByUserAndDailyDateAndPunchOrder(
                                user, now.toLocalDate(), punchOrder)
                        .orElseThrow();
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    /* ----------------------------------------------------------------------
     *  5)  autoPunchOut  – bucht Delta & ergänzt fehlende Punches
     * --------------------------------------------------------------------*/
    @Scheduled(cron = "0 20 23 * * *", zone = "Europe/Zurich")
    @Transactional
    public void autoPunchOut() {

        LocalDateTime now         = LocalDateTime.now();          // aktueller Zeitpunkt
        LocalDate     today       = now.toLocalDate();
        LocalDateTime startOfDay  = today.atStartOfDay();

        /* 1) Alle offenen Punches von heute holen */
        List<TimeTracking> openPunches =
                timeTrackingRepository.findByEndTimeIsNullAndStartTimeBetween(startOfDay, now);

    /* 2) Nach Benutzer gruppieren, damit completeMissingPunches
          pro User nur EINMAL aufgerufen wird                        */
        Map<User, List<TimeTracking>> byUser = openPunches.stream()
                .collect(Collectors.groupingBy(TimeTracking::getUser));

        /* 3) Fehlende WORK_END-Punches (#4) ergänzen                     */
        byUser.forEach((user, punches) -> completeMissingPunches(punches, user, now));

        log.info("✅ AutoPunchOut beendet – {}", today);
    }

    /* ----------------------------------------------------------------------
     *  6)  bookDailyBalances  – Fallback 00:05 Uhr
     * --------------------------------------------------------------------*/
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void bookDailyBalances() {

        LocalDate yesterday = LocalDate.now().minusDays(1);
        userRepository.findAll()
                .forEach(u -> {
                    int delta = computeDailyWorkDifference(u, yesterday.toString());
                    u.setTrackingBalanceInMinutes(
                            Optional.ofNullable(u.getTrackingBalanceInMinutes())
                                    .orElse(0) + delta);
                });
        log.info("✅ Daily balances for {} booked.", yesterday);
    }


    private void completeMissingPunches(List<TimeTracking> list,
                                        User user,
                                        LocalDateTime punchOutTime) {

        list.sort(Comparator.comparingInt(TimeTracking::getPunchOrder));
        int last = list.get(list.size() - 1).getPunchOrder();

        if (last < 4) {
            // ①  Gibt es evtl. schon einen automatisch oder manuell angelegten #4?
            Optional<TimeTracking> existing =
                    timeTrackingRepository.findFirstByUserAndDailyDateAndPunchOrder(
                            user, punchOutTime.toLocalDate(), 4);
            if (existing.isPresent()) {
                log.warn("AutoPunchOut {}: Punch-#4 bereits vorhanden – überspringe.", user.getUsername());
                return;
            }

            TimeTracking end = new TimeTracking();
            end.setUser(user);
            end.setPunchOrder(4);
            end.setDailyDate(punchOutTime.toLocalDate());
            end.setStartTime(punchOutTime);
            end.setEndTime(punchOutTime);
            end.setWorkEnd(punchOutTime.toLocalTime());

            try {                                         // ②  Duplicate-Safety-Net
                timeTrackingRepository.save(end);
                log.info("AutoPunchOut {}: offener Punch #{} geschlossen", user.getUsername(), last);
            } catch (DataIntegrityViolationException dup) {
                log.warn("DuplicateKey bei AutoPunchOut {}, Punch-#4 – hole bestehenden DS.", user.getUsername());
            }
        }
    }



    /* ----------------------------------------------------------------------
     *  8)  handlePercentagePunch  – nutzt zentrale Konstante
     * --------------------------------------------------------------------*/
    public PercentagePunchResponse handlePercentagePunch(String username,
                                                         double percentage) {

        int expected = (int) Math.round(FULL_DAY_MINUTES * (percentage / 100.0));
        User user    = loadUserByUsername(username);

        LocalDate today = LocalDate.now();
        LocalDateTime s = today.atStartOfDay(), e = today.plusDays(1).atStartOfDay();

        int worked = timeTrackingRepository
                .findByUserAndStartTimeBetween(user, s, e).stream()
                .filter(tt -> tt.getStartTime() != null && tt.getEndTime() != null)
                .mapToInt(tt -> (int) ChronoUnit.MINUTES
                        .between(tt.getStartTime(), tt.getEndTime()))
                .sum();

        int diff = worked - expected;
        String msg = diff >= 0 ? "Überstunden: " + diff + " min"
                : "Fehlstunden: " + (-diff) + " min";

        return new PercentagePunchResponse(username, worked, expected, diff, msg);
    }


    // -------------------------------------------------------------------------
    // HISTORY, DAILY NOTE, REPORT, & CORRECTIONS
    // -------------------------------------------------------------------------
    /**
     * Gibt eine Liste aller Stempel als TimeTrackingResponse zurück,
     * absteigend nach StartTime sortiert.
     */
    public List<TimeTrackingResponse> getUserHistory(String username) {
        User user = loadUserByUsername(username);
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByStartTimeDesc(user);
        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Transactional
    public TimeTrackingResponse updateDailyNote(String username, String date, String note) {
        User user = loadUserByUsername(username);
        if (!user.isHourly()) {
            throw new RuntimeException("Daily note is only available for hourly employees.");
        }
        LocalDate localDate = LocalDate.parse(date);
        List<TimeTracking> noteEntries =
                timeTrackingRepository.findDailyNoteByUserAndDate(user, localDate);

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

        logger.info("updateDailyNote: Daily note für {} am {}: '{}'", username, date, note);
        return convertToResponse(noteEntry);
    }

    /**
     *  Aktualisiert (bzw. legt neu an) alle vier Stempel eines Tages
     *  – inklusive kompletter Neuberechnung der globalen Tracking-Bilanz.
     */
    @Transactional
    public String updateDayTimeEntries(
            String targetUsername,
            String date,
            String workStartStr,
            String breakStartStr,
            String breakEndStr,
            String workEndStr,
            String adminUsername,
            String adminPassword,
            String userPassword
    ) {
        /* ---------- 1) Security ---------- */
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        checkAdminAndUserPasswords(targetUser, adminUsername, adminPassword, userPassword);

        /* ---------- 2) Eingaben parsen ---------- */
        LocalDate parsedDate       = LocalDate.parse(date);
        LocalTime newWorkStart     = LocalTime.parse(workStartStr);
        LocalTime newWorkEnd       = LocalTime.parse(workEndStr);

        boolean   hasBreak         = !(breakStartStr.equals("00:00") && breakEndStr.equals("00:00"));
        LocalTime newBreakStart    = hasBreak ? LocalTime.parse(breakStartStr) : null;
        LocalTime newBreakEnd      = hasBreak ? LocalTime.parse(breakEndStr)   : null;

        LocalDateTime newWorkStartDT  = parsedDate.atTime(newWorkStart);
        LocalDateTime newWorkEndDT    = parsedDate.atTime(newWorkEnd);
        LocalDateTime newBreakStartDT = hasBreak ? parsedDate.atTime(newBreakStart) : null;
        LocalDateTime newBreakEndDT   = hasBreak ? parsedDate.atTime(newBreakEnd)   : null;

        /* ---------- 3) ALTE Einträge zu diesem Tag VOLLSTÄNDIG entfernen ---------- */
        int deleted = timeTrackingRepository.deleteByUserAndDailyDate(targetUser, parsedDate);
        logger.info("updateDayTimeEntries: {} alte Einträge für {} gelöscht.", deleted, date);

        /* ---------- 4) NEUE Einträge anlegen (dailyDate immer setzen!) ---------- */

        // WORK_START (#1)
        TimeTracking ts1 = new TimeTracking();
        ts1.setUser(targetUser);
        ts1.setPunchOrder(1);
        ts1.setDailyDate(parsedDate);
        ts1.setStartTime(newWorkStartDT);
        ts1.setWorkStart(newWorkStart);
        ts1.setCorrected(true);
        timeTrackingRepository.save(ts1);

        // BREAK_START + BREAK_END
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

        // WORK_END (#4)
        TimeTracking ts4 = new TimeTracking();
        ts4.setUser(targetUser);
        ts4.setPunchOrder(4);
        ts4.setDailyDate(parsedDate);
        ts4.setStartTime(newWorkEndDT);
        ts4.setEndTime(newWorkEndDT);
        ts4.setWorkEnd(newWorkEnd);
        ts4.setCorrected(true);
        timeTrackingRepository.save(ts4);

        /* ---------- 5) Balance neu berechnen ---------- */
        try {
            List<String> trackedDates =
                    timeTrackingRepository.findAllTrackedDateStringsByUser(targetUser.getId());

            int newBalance = 0;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (String dStr : trackedDates) {
                newBalance += computeDailyWorkDifference(targetUser, dStr);
            }
            targetUser.setTrackingBalanceInMinutes(newBalance);
            userRepository.save(targetUser);
            logger.info("✅ Balance für {} neu berechnet: {} min", targetUsername, newBalance);

        } catch (Exception ex) {
            logger.warn("⚠️  Fehler bei Balance-Refresh: {}", ex.getMessage());
        }

        return "Day entries updated successfully";
    }

    /**
     * Lädt sämtliche Zeiterfassungs-Datensätze im angegebenen Datumsbereich
     * und wandelt sie in eine Tag-für-Tag-Übersicht um (TimeReportDTO).
     */
    public List<TimeReportDTO> getReport(String username, String startDateStr, String endDateStr) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        User user = loadUserByUsername(username);

        List<TimeTracking> entries = timeTrackingRepository
                .findByUserAndStartTimeBetween(user, startDateTime, endDateTime);

        // Gruppieren nach Tag
        Map<LocalDate, List<TimeTracking>> grouped = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getStartTime().toLocalDate()));

        List<TimeReportDTO> report = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d.M.yyyy", Locale.GERMAN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // Sortierte Liste von Tagen
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
                int order = (e.getPunchOrder() != null) ? e.getPunchOrder() : 0;
                if (order == 1 && workStart.equals("-")) {
                    workStart = (e.getWorkStart() != null)
                            ? e.getWorkStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (order == 2 && breakStart.equals("-")) {
                    breakStart = (e.getBreakStart() != null)
                            ? e.getBreakStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (order == 3 && breakEnd.equals("-")) {
                    breakEnd = (e.getBreakEnd() != null)
                            ? e.getBreakEnd().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                } else if (order == 4 && workEnd.equals("-")) {
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
        return report;
    }

    /* ----------------------------------------------------------------------
     *  7)  computeDailyWorkDifference  – Netto (ohne Pause)
     * --------------------------------------------------------------------*/
    public int computeDailyWorkDifference(User user, String isoDate) {

        // ------------------------------------------------------------
        // 1) Zeitfenster und Tages-Soll
        // ------------------------------------------------------------
        LocalDate day           = LocalDate.parse(isoDate);
        LocalDateTime startDay  = day.atStartOfDay();
        LocalDateTime endDay    = day.plusDays(1).atStartOfDay();
        int expectedMinutes     = getDailyTargetMinutes(user);

        // ------------------------------------------------------------
        // 2) Tages-Einträge holen
        //    – bevorzugt die *korrigierten* Einträge
        //      (existiert kein korrigierter, nehmen wir den ersten)
        // ------------------------------------------------------------
        List<TimeTracking> all = timeTrackingRepository
                .findByUserAndStartTimeBetween(user, startDay, endDay);

        Map<Integer, TimeTracking> byOrder = new HashMap<>();
        for (TimeTracking t : all) {
            Integer order = Optional.ofNullable(t.getPunchOrder()).orElse(0);
            if (order == 0) continue;                 // Sicherheit

            TimeTracking existing = byOrder.get(order);
            if (existing == null || (!existing.isCorrected() && t.isCorrected())) {
                byOrder.put(order, t);                // korrigiert > unkorrigiert
            }
        }

        LocalDateTime ws = toDateTime(byOrder.get(1));   // work-start
        LocalDateTime bs = toDateTime(byOrder.get(2));   // break-start
        LocalDateTime be = toDateTime(byOrder.get(3));   // break-end
        LocalDateTime we = toDateTime(byOrder.get(4));   // work-end

        // ------------------------------------------------------------
        // 3) Netto-Arbeitszeit berechnen
        // ------------------------------------------------------------
        int worked = 0;
        if (ws != null && we != null) {
            if (bs != null && be != null) {                   // Pause vorhanden
                worked += ChronoUnit.MINUTES.between(ws, bs);
                worked += ChronoUnit.MINUTES.between(be, we);
            } else {                                          // keine Pause
                worked += ChronoUnit.MINUTES.between(ws, we);
            }
        }

        return worked - expectedMinutes;
    }

    /* ---------- kleine Helfer ---------- */
    private LocalDateTime toDateTime(TimeTracking t) {
        if (t == null) return null;
        return Optional.ofNullable(t.getStartTime()).orElse(t.getEndTime());
    }

    private int getDailyTargetMinutes(User user) {

        if (user.isHourly()) return 0;                       // Werkstudent

        if (Boolean.TRUE.equals(user.getIsPercentage())) {
            double perc = Optional.ofNullable(user.getWorkPercentage())
                    .orElse((int) 100d);
            return (int) Math.round(FULL_DAY_MINUTES * (perc / 100.0));
        }
        return FULL_DAY_MINUTES;                             // Vollzeit
    }

    /* ----------------------------------------------------------------------
     *  9)  getDailyVacationMinutes  – skaliert sauber
     * --------------------------------------------------------------------*/
    private int getDailyVacationMinutes(User u) {
        if (!Boolean.TRUE.equals(u.getIsPercentage())) return FULL_DAY_MINUTES;
        return (int) Math.round(FULL_DAY_MINUTES * (u.getWorkPercentage() / 100.0));
    }


    public int getWeeklyBalance(User user, LocalDate monday) {
        // (Optional, falls benötigt) ...
        LocalDate sunday = monday.plusDays(6);

        int workedMins = timeTrackingRepository
                .findByUserAndStartTimeBetween(user, monday.atStartOfDay(), sunday.plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.groupingBy(
                        tt -> tt.getStartTime().toLocalDate(),
                        Collectors.summingInt(tt -> {
                            if (tt.getStartTime() != null && tt.getEndTime() != null) {
                                return (int) Duration.between(tt.getStartTime(), tt.getEndTime()).toMinutes();
                            }
                            return 0;
                        })
                ))
                .values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();

        // ggf. VacationRequests ...
        return workedMins;
    }

    // -------------------------------------------------------------------------
    // ADMIN HELPER
    // -------------------------------------------------------------------------
    public List<TimeTracking> getTimeTrackingEntriesForUserAndDate(User user, LocalDateTime start, LocalDateTime end) {
        return timeTrackingRepository.findByUserAndStartTimeBetween(user, start, end);
    }

    public List<AdminTimeTrackDTO> getAllTimeTracksWithUser() {
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
                .orElseThrow(() -> new RuntimeException("Time tracking entry not found"));
        User targetUser = tt.getUser();

        checkAdminAndUserPasswords(targetUser, adminUsername, adminPassword, userPassword);

        tt.setStartTime(newStart);
        tt.setEndTime(newEnd);
        TimeTracking updated = timeTrackingRepository.save(tt);
        logger.info("updateTimeTrackEntry: Eintrag {} aktualisiert: Start={}, End={}",
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

    /**
     * Prüft, ob Admin- und Userpasswörter korrekt sind
     * – bei Fremd-Bearbeitung muss Admin-PW bestehen.
     */
    private void checkAdminAndUserPasswords(User targetUser,
                                            String adminUsername,
                                            String adminPassword,
                                            String userPassword) {
        if (adminUsername != null && !adminUsername.trim().isEmpty()) {
            User adminUser = userRepository.findByUsername(adminUsername)
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            String storedAdminPwd = adminUser.getAdminPassword();
            if (storedAdminPwd == null || storedAdminPwd.trim().isEmpty()) {
                storedAdminPwd = adminUser.getPassword();
                logger.info("checkAdminAndUserPasswords: Kein separates Admin-Passwort, verwende Login-Passwort.");
            }

            // Selbst-Bearbeitung?
            if (targetUser.getUsername().equals(adminUsername)) {
                // Self-Edit => adminPassword und userPassword müssen matchen
                if (!adminPassword.equals(userPassword)) {
                    throw new RuntimeException("For self-edit, admin and user passwords must match");
                }
                boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
                boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
                if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
                if (!userPwdMatch)  throw new RuntimeException("Invalid user password");
            } else {
                // Fremd-Bearbeitung
                boolean adminPwdMatch = passwordEncoder.matches(adminPassword, storedAdminPwd);
                boolean userPwdMatch  = passwordEncoder.matches(userPassword, targetUser.getPassword());
                if (!adminPwdMatch) throw new RuntimeException("Invalid admin password");
                if (!userPwdMatch)  throw new RuntimeException("Invalid user password");
            }
        }
    }

    private TimeTrackingResponse convertToResponse(TimeTracking tt) {
        Integer pOrder = (tt.getPunchOrder() != null) ? tt.getPunchOrder() : 0;
        String label;
        switch (pOrder) {
            case 1: label = "Work Start";  break;
            case 2: label = "Break Start"; break;
            case 3: label = "Break End";   break;
            case 4: label = "Work End";    break;
            case 0: label = "Daily Note";  break;
            default: label = "Unknown";
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
}
