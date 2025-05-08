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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    // -------------------------------------------------------------------------
    // 1) Punch-Logik (WORK_START, BREAK_START, BREAK_END, WORK_END)
    // -------------------------------------------------------------------------

    /**
     * Direkte Punch-Aktion (WORK_START, BREAK_START, BREAK_END, WORK_END).
     */
    @Transactional
    public TimeTrackingResponse handlePunch(String username, String newStatus) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        LocalDate   today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

    /* -------------------------------------------------------------
       a)  Letzten Punch VON HEUTE ermitteln (–> Übergangskontrolle)
       ------------------------------------------------------------- */
        Integer lastPunchOrder = timeTrackingRepository
                .findTopByUserAndDailyDateOrderByPunchOrderDesc(user, today)
                .map(TimeTracking::getPunchOrder)
                .orElse(4);                     // noch nichts → so tun als ob 4 (WORK_END) liegt

        logger.info("handlePunch: Für '{}' letzter Punch heute = {}", username, lastPunchOrder);
        logger.info("handlePunch: Gewünschter neuer Status    = {}", newStatus);

        if (!isTransitionAllowed(lastPunchOrder, newStatus)) {
            throw new RuntimeException("Transition not allowed from " + lastPunchOrder + " to " + newStatus);
        }

    /* -------------------------------------------------------------
       b)  Offenen Punch von HEUTE beenden (falls vorhanden)
       ------------------------------------------------------------- */
        timeTrackingRepository.findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(user)
                .filter(tt -> tt.getDailyDate().equals(today))
                .ifPresent(tt -> {
                    tt.setEndTime(now);
                    timeTrackingRepository.saveAndFlush(tt);
                    logger.info("handlePunch: Offener Punch (Order {}) für '{}' geschlossen.", tt.getPunchOrder(), username);
                });

    /* -------------------------------------------------------------
       c)  Neuen Punch anlegen  – inkl.  DUPLICATE-Key Safety-Net
       ------------------------------------------------------------- */
        int nextOrder = mapStatusToPunchOrder(newStatus);

        try {
            TimeTracking created = createNewPunch(user, nextOrder);
            return convertToResponse(created);

        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // Parallel-Request war schneller → bestehenden Datensatz zurückgeben
            return timeTrackingRepository
                    .findByUserAndDailyDateAndPunchOrder(user, today, nextOrder) // List<TimeTracking>
                    .stream()                                                   // → Stream<…>
                    .findFirst()                                                // → Optional<…>
                    .map(this::convertToResponse)                               // unser DTO
                    .orElseThrow(() -> new IllegalStateException(
                            "Duplicate-Key-Eintrag nicht auffindbar"));
        }
    }

    /**
     * "Smart Punch" – erkennt automatisch, welcher Stempel (1..4) als Nächstes dran ist.
     */
    @Transactional
    public TimeTrackingResponse handleSmartPunch(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        LocalDate   today      = LocalDate.now();
        LocalDateTime startDay = today.atStartOfDay();
        LocalDateTime endDay   = today.atTime(23, 59, 59);

    /* -------------------------------------------------------------
       a)  Bei Vollzeit-User: einmaliger Tageszyklus erzwungen
       ------------------------------------------------------------- */
        if (!user.getIsPercentage()) {
            boolean alreadyFinished = timeTrackingRepository
                    .findTopByUserAndEndTimeIsNotNullAndStartTimeBetweenOrderByStartTimeDesc(user, startDay, endDay)
                    .filter(tt -> tt.getPunchOrder() == 4)
                    .isPresent();
            if (alreadyFinished) {
                throw new RuntimeException("Für heute ist bereits ein kompletter Zyklus abgeschlossen.");
            }
        }

    /* -------------------------------------------------------------
       b)  Letzten Punch von HEUTE holen
       ------------------------------------------------------------- */
        Integer lastPunchOrder = timeTrackingRepository
                .findTopByUserAndDailyDateOrderByPunchOrderDesc(user, today)
                .map(TimeTracking::getPunchOrder)
                .orElse(4);                        // noch nichts → so tun als ob 4

    /* -------------------------------------------------------------
       c)  Offenen Punch von heute schließen
       ------------------------------------------------------------- */
        timeTrackingRepository.findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(user)
                .filter(tt -> tt.getDailyDate().equals(today))
                .ifPresent(tt -> {
                    tt.setEndTime(LocalDateTime.now());
                    timeTrackingRepository.saveAndFlush(tt);
                    logger.info("handleSmartPunch: Offener Punch (Order {}) für '{}' geschlossen.", tt.getPunchOrder(), username);
                });

    /* -------------------------------------------------------------
       d)  Nächsten Status bestimmen
       ------------------------------------------------------------- */
        String nextStatus = switch (lastPunchOrder) {
            case 4  -> "WORK_START";
            case 1  -> "BREAK_START";
            case 2  -> "BREAK_END";
            case 3  -> "WORK_END";
            default -> "WORK_START";
        };
        logger.info("handleSmartPunch: Nächster Status = {}", nextStatus);

    /* -------------------------------------------------------------
       e)  Delegieren an handlePunch   (+ evtl. Überstundenupdate)
       ------------------------------------------------------------- */
        TimeTrackingResponse resp = handlePunch(username, nextStatus);

        if ("WORK_END".equals(nextStatus)) {
            int delta = computeDailyWorkDifference(user, today.toString());
            user.setTrackingBalanceInMinutes(
                    Optional.ofNullable(user.getTrackingBalanceInMinutes()).orElse(0) + delta);
            userRepository.save(user);
            logger.info("Überstunden aktualisiert für '{}': Δ={} min, neuer Stand={} min",
                    username, delta, user.getTrackingBalanceInMinutes());
        }
        return resp;
    }



    // -------------------------------------------------------------------------
    // 2) AutoPunchOut – automatisches Schließen offener Einträge
    // -------------------------------------------------------------------------
    /**
     * Schließt täglich gegen 23:20 (cron) alle offenen Stempel ab.
     * Du kannst isPercentage-User hier ausschließen, wenn du willst.
     */
    @Scheduled(cron = "0 20 23 * * *")
    @Transactional
    public void autoPunchOut() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime autoEndTime = today.atTime(23, 30, 59);

        logger.info("AutoPunchOut gestartet für {}. StartOfDay={}, AutoEndTime={}", today, startOfDay, autoEndTime);
        try {
            List<TimeTracking> openPunches = timeTrackingRepository
                    .findByEndTimeIsNullAndStartTimeBetween(startOfDay, autoEndTime);
            logger.info("AutoPunchOut: Gefundene offene Einträge: {}", openPunches.size());

            Map<User, List<TimeTracking>> userMap = openPunches.stream()
                    .collect(Collectors.groupingBy(TimeTracking::getUser));

            for (Map.Entry<User, List<TimeTracking>> entry : userMap.entrySet()) {
                User user = entry.getKey();

                // Falls isPercentage=true nicht automatisch ausgestempelt werden soll:
                if (user.getIsPercentage()) {
                    logger.info("AutoPunchOut: User {} ist Prozent-User, wende Speziallogik an.", user.getUsername());

                    List<TimeTracking> entriesToday = timeTrackingRepository
                            .findByUserAndStartTimeBetween(user, startOfDay, autoEndTime);

                    Set<Integer> punchOrders = entriesToday.stream()
                            .map(e -> e.getPunchOrder() != null ? e.getPunchOrder() : 0)
                            .collect(Collectors.toSet());

                    if (punchOrders.size() == 1 && punchOrders.contains(1)) {
                        // Nur WORK_START -> Ergänze WORK_END
                        TimeTracking workEnd = new TimeTracking();
                        workEnd.setUser(user);
                        workEnd.setPunchOrder(4);
                        workEnd.setStartTime(autoEndTime.plusSeconds(40));
                        workEnd.setEndTime(autoEndTime.plusSeconds(40));
                        workEnd.setCorrected(true);
                        timeTrackingRepository.save(workEnd);
                        logger.info("AutoPunchOut (Percentage): WORK_END ergänzt für {}", user.getUsername());

                    } else if (punchOrders.size() == 2 && punchOrders.contains(1) && punchOrders.contains(2)) {
                        // WORK_START + BREAK_START -> Ignoriere BREAK, setzte WORK_END
                        // Optional: Alte BREAK_START auf Korrektur setzen
                        for (TimeTracking e : entriesToday) {
                            if (e.getPunchOrder() == 2) {
                                timeTrackingRepository.delete(e); // Lösche die Break Start, da sie sinnlos ist
                                logger.info("AutoPunchOut (Percentage): BREAK_START gelöscht für {}", user.getUsername());
                            }
                        }
                        TimeTracking workEnd = new TimeTracking();
                        workEnd.setUser(user);
                        workEnd.setPunchOrder(4);
                        workEnd.setStartTime(autoEndTime.plusSeconds(40));
                        workEnd.setEndTime(autoEndTime.plusSeconds(40));
                        workEnd.setCorrected(true);
                        timeTrackingRepository.save(workEnd);
                        logger.info("AutoPunchOut (Percentage): WORK_END ergänzt für {}", user.getUsername());

                    } else if (punchOrders.size() == 3 || !punchOrders.contains(4)) {
                        // Nur Teile fehlen → auffüllen wie bei normalen Usern
                        for (int i = 1; i <= 4; i++) {
                            final int punch = i;
                            if (!punchOrders.contains(punch)) {
                                TimeTracking newPunch = new TimeTracking();
                                newPunch.setUser(user);
                                newPunch.setPunchOrder(punch);
                                LocalDateTime punchTime = autoEndTime.plusSeconds(punch * 10);
                                newPunch.setStartTime(punchTime);
                                if (punch == 3 || punch == 4) {
                                    newPunch.setEndTime(punchTime);
                                }
                                newPunch.setCorrected(true);
                                timeTrackingRepository.save(newPunch);
                                logger.info("AutoPunchOut (Percentage): Punch {} ergänzt für {}", punch, user.getUsername());
                            }
                        }
                    }

                    continue;
                }


                logger.info("AutoPunchOut: Bearbeite offene Einträge für {}", user.getUsername());
                try {
                    List<TimeTracking> entriesToday = timeTrackingRepository
                            .findByUserAndStartTimeBetween(user, startOfDay, autoEndTime);
                    if (entriesToday.isEmpty()) continue;

                    Set<Integer> orders = entriesToday.stream()
                            .map(tt -> tt.getPunchOrder() != null ? tt.getPunchOrder() : 0)
                            .collect(Collectors.toSet());
                    logger.info("AutoPunchOut: {} hat PunchOrders: {}", user.getUsername(), orders);

                    // Falls 1..4 komplett => tue nichts
                    if (orders.contains(1) && orders.contains(2) && orders.contains(3) && orders.contains(4)) {
                        logger.info("AutoPunchOut: {} hat bereits einen vollständigen Zyklus (1..4).", user.getUsername());
                        continue;
                    }

                    // Erzeuge fehlende Punches
                    for (int i = 1; i <= 4; i++) {
                        final int punch = i;
                        if (!orders.contains(punch)) {
                            TimeTracking newPunch = new TimeTracking();
                            newPunch.setUser(user);
                            newPunch.setPunchOrder(punch);
                            LocalDateTime punchTime = autoEndTime.plusSeconds(punch * 10);
                            newPunch.setStartTime(punchTime);
                            if (punch == 3 || punch == 4) {
                                newPunch.setEndTime(punchTime);
                            }
                            newPunch.setCorrected(true);
                            timeTrackingRepository.save(newPunch);
                            logger.info("AutoPunchOut: Fehlender Punch {} für {} angelegt.", punch, user.getUsername());
                        } else {
                            Optional<TimeTracking> openEntryOpt = entriesToday.stream()
                                    .filter(tt -> tt.getPunchOrder() == punch && tt.getEndTime() == null)
                                    .findFirst();
                            if (openEntryOpt.isPresent()) {
                                TimeTracking openEntry = openEntryOpt.get();
                                LocalDateTime newEndTime = autoEndTime.plusSeconds(punch * 10);
                                openEntry.setEndTime(newEndTime);
                                openEntry.setCorrected(true);
                                timeTrackingRepository.save(openEntry);
                                logger.info("AutoPunchOut: Offener Punch {} für {} beendet.", punch, user.getUsername());
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Fehler bei AutoPunchOut für {}: {}", user.getUsername(), ex.getMessage());
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            logger.error("Allg. Fehler AutoPunchOut: {}", ex.getMessage());
            ex.printStackTrace();
        }
        logger.info("AutoPunchOut für {} abgeschlossen.", today);
    }

    // -------------------------------------------------------------------------
    // 3) Percentage-Punch (nur Tagesberechnung)
    // -------------------------------------------------------------------------
    @Transactional
    public PercentagePunchResponse handlePercentagePunch(String username, double percentage) {
        // Hardcodiert: 42,5h/Woche => 8,5h (510min) / Tag => skaliere um "percentage" %
        int fullDayMinutes = 510;
        int expectedMinutes = (int) Math.round(fullDayMinutes * (percentage / 100.0));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for percentage punch"));

        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();

        List<TimeTracking> entries = getTimeTrackingEntriesForUserAndDate(user, dayStart, dayEnd);
        int workedMinutes = 0;
        for (TimeTracking entry : entries) {
            if (entry.getStartTime() != null && entry.getEndTime() != null) {
                workedMinutes += ChronoUnit.MINUTES.between(entry.getStartTime(), entry.getEndTime());
            }
        }

        int difference = workedMinutes - expectedMinutes;
        String message = (difference >= 0)
                ? "Überstunden: " + difference + " Minuten."
                : "Fehlstunden: " + (-difference) + " Minuten.";

        return new PercentagePunchResponse(username, workedMinutes, expectedMinutes, difference, message);
    }

    // -------------------------------------------------------------------------
    // 4) Notizen, Korrekturen, getReport, computeDailyWorkDifference
    // -------------------------------------------------------------------------

    /**
     * **Fehlte zuvor**: Gibt eine Liste aller Stempel als TimeTrackingResponse zurück,
     * absteigend nach StartTime sortiert.
     */
    public List<TimeTrackingResponse> getUserHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByStartTimeDesc(user);
        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Transactional
    public TimeTrackingResponse updateDailyNote(String username, String date, String note) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!user.isHourly()) {
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

        logger.info("updateDailyNote: Daily note für {} am {}: '{}'", username, date, note);
        return convertToResponse(noteEntry);
    }

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
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        checkAdminAndUserPasswords(targetUser, adminUsername, adminPassword, userPassword);

        LocalDate parsedDate = LocalDate.parse(date);
        LocalTime newWorkStart = LocalTime.parse(workStartStr);
        LocalTime newWorkEnd   = LocalTime.parse(workEndStr);
        boolean hasBreak       = !(breakStartStr.equals("00:00") && breakEndStr.equals("00:00"));
        LocalTime newBreakStart = hasBreak ? LocalTime.parse(breakStartStr) : null;
        LocalTime newBreakEnd   = hasBreak ? LocalTime.parse(breakEndStr)   : null;

        LocalDateTime newWorkStartDT  = parsedDate.atTime(newWorkStart);
        LocalDateTime newWorkEndDT    = parsedDate.atTime(newWorkEnd);
        LocalDateTime newBreakStartDT = hasBreak ? parsedDate.atTime(newBreakStart) : null;
        LocalDateTime newBreakEndDT   = hasBreak ? parsedDate.atTime(newBreakEnd)   : null;

        LocalDateTime dayStart = parsedDate.atStartOfDay();
        LocalDateTime dayEnd   = parsedDate.plusDays(1).atStartOfDay();

        // Alte Einträge löschen
        List<TimeTracking> oldEntries = timeTrackingRepository
                .findByUserAndStartTimeBetween(targetUser, dayStart, dayEnd);
        timeTrackingRepository.deleteAll(oldEntries);
        logger.info("updateDayTimeEntries: {} alte Einträge für {} gelöscht.", oldEntries.size(), date);

        // WORK_START
        TimeTracking ts1 = new TimeTracking();
        ts1.setUser(targetUser);
        ts1.setPunchOrder(1);
        ts1.setStartTime(newWorkStartDT);
        ts1.setWorkStart(newWorkStart);
        ts1.setCorrected(true);
        timeTrackingRepository.save(ts1);

        // BREAKS
        if (hasBreak) {
            TimeTracking ts2 = new TimeTracking();
            ts2.setUser(targetUser);
            ts2.setPunchOrder(2);
            ts2.setStartTime(newBreakStartDT);
            ts2.setBreakStart(newBreakStart);
            ts2.setCorrected(true);
            timeTrackingRepository.save(ts2);

            TimeTracking ts3 = new TimeTracking();
            ts3.setUser(targetUser);
            ts3.setPunchOrder(3);
            ts3.setStartTime(newBreakEndDT);
            ts3.setBreakEnd(newBreakEnd);
            ts3.setCorrected(true);
            timeTrackingRepository.save(ts3);
        }

        // WORK_END
        TimeTracking ts4 = new TimeTracking();
        ts4.setUser(targetUser);
        ts4.setPunchOrder(4);
        ts4.setStartTime(newWorkEndDT);
        ts4.setEndTime(newWorkEndDT);
        ts4.setWorkEnd(newWorkEnd);
        ts4.setCorrected(true);
        timeTrackingRepository.save(ts4);

        // ✅ GLOBALER BALANCE-REBUILD MIT MANUELLER DATUMS-KONVERTIERUNG
        try {
            List<String> trackedDateStrings = timeTrackingRepository.findAllTrackedDateStringsByUser(targetUser.getId());

            int newGlobalBalance = 0;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (String dateStr : trackedDateStrings) {
                LocalDate d = LocalDate.parse(dateStr, formatter);
                int tDelta = computeDailyWorkDifference(targetUser, d.toString());
                newGlobalBalance += tDelta;
            }

            targetUser.setTrackingBalanceInMinutes(newGlobalBalance);
            userRepository.save(targetUser);
            logger.info("✅ Balance für {} komplett neu berechnet: {}min", targetUsername, newGlobalBalance);
        } catch (Exception ex) {
            logger.warn("⚠️ Fehler beim Balance-Neuaufbau: {}", ex.getMessage());
        }

        return "Day entries updated successfully (with balance recalculated)";
    }


    /**
     * Lädt sämtliche Zeiterfassungs-Datensätze im angegebenen Datumsbereich und
     * wandelt sie in eine Tag-für-Tag-Übersicht um (TimeReportDTO).
     */
    public List<TimeReportDTO> getReport(String username, String startDateStr, String endDateStr) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

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
                int order = e.getPunchOrder() != null ? e.getPunchOrder() : 0;
                // WORK_START
                if (order == 1 && workStart.equals("-")) {
                    workStart = e.getWorkStart() != null
                            ? e.getWorkStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                }
                // BREAK_START
                else if (order == 2 && breakStart.equals("-")) {
                    breakStart = e.getBreakStart() != null
                            ? e.getBreakStart().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                }
                // BREAK_END
                else if (order == 3 && breakEnd.equals("-")) {
                    breakEnd = e.getBreakEnd() != null
                            ? e.getBreakEnd().format(timeFormatter)
                            : e.getStartTime().format(timeFormatter);
                }
                // WORK_END
                else if (order == 4 && workEnd.equals("-")) {
                    if (e.getWorkEnd() != null) {
                        workEnd = e.getWorkEnd().format(timeFormatter);
                    } else if (e.getEndTime() != null) {
                        workEnd = e.getEndTime().format(timeFormatter);
                    }
                }
                // DailyNote
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

    /**
     * Berechnet die Ist-Soll-Differenz für einen bestimmten Tag.
     * isPercentage => rechnet den Tages-Soll mit workPercentage.
     */
    public int computeDailyWorkDifference(User user, String date) {
        LocalDate parsedDate = LocalDate.parse(date);
        LocalDateTime dayStart = parsedDate.atStartOfDay();
        LocalDateTime dayEnd = parsedDate.plusDays(1).atStartOfDay();

        // Sollzeit berechnen
        int expectedMinutes;
        if (user.getIsPercentage()) {
            expectedMinutes = (int) Math.round(510 * (user.getWorkPercentage() / 100.0));
        } else if (user.isHourly()) {
            expectedMinutes = 0;
        } else {
            expectedMinutes = 480;
        }

        // Alle Einträge für den Tag holen & nach PunchOrder sortieren
        List<TimeTracking> entries = timeTrackingRepository
                .findByUserAndStartTimeBetween(user, dayStart, dayEnd);

        entries.sort(Comparator.comparing(TimeTracking::getPunchOrder));

        int actualMinutes = 0;
        for (int i = 0; i < entries.size(); i++) {
            TimeTracking current = entries.get(i);

            LocalDateTime start = current.getStartTime();
            LocalDateTime end = current.getEndTime();

            // Wenn kein endTime gesetzt → nimm den nächsten Punch als Endzeit
            if (end == null && i + 1 < entries.size()) {
                end = entries.get(i + 1).getStartTime();
            }

            if (start != null && end != null) {
                actualMinutes += ChronoUnit.MINUTES.between(start, end);
            }
        }

        return actualMinutes - expectedMinutes;
    }

    private int getDailyVacationMinutes(User user) {
        // 🔁 Vollzeit (oder kein Prozent-Modus)
        if (!Boolean.TRUE.equals(user.getIsPercentage())) {
            return 510; // 8.5h in Minuten
        }

        double percentage = Optional.ofNullable(user.getWorkPercentage()).orElse((int) 100.0);
        double weeklyMinutes = 42.5 * 60 * (percentage / 100.0); // z. B. 60% → 1530 min/Woche

        int workdays = 0;

        try {
            List<Map<String, Double>> schedule = user.getWeeklySchedule();
            if (schedule != null && !schedule.isEmpty()) {
                for (Map<String, Double> dayMap : schedule) {
                    for (Double value : dayMap.values()) {
                        if (value != null && value > 0) {
                            workdays++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Fehler beim Auswerten des Wochenplans für '{}'", user.getUsername(), e);
        }

        // Fallback auf 5-Tage-Woche wenn kein Plan vorhanden
        if (workdays == 0) workdays = 5;

        return (int) Math.round(weeklyMinutes / workdays);
    }


    public int getWeeklyBalance(User user, LocalDate monday) {
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

        int vacationMins = user.getVacationRequests().stream()
                .filter(req ->
                        !req.isDenied() &&
                                req.isApproved() &&
                                Boolean.TRUE.equals(req.isUsesOvertime()) &&
                                !req.getStartDate().isAfter(sunday) &&
                                !req.getEndDate().isBefore(monday)
                )
                .mapToInt(req -> {
                    int days = (int) ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate()) + 1;
                    int daily = getDailyVacationMinutes(user);
                    if (req.isHalfDay()) daily /= 2;
                    return days * daily;
                }).sum();

        return workedMins + vacationMins;
    }


    // -------------------------------------------------------------------------
    // 5) Hilfsfunktionen
    // -------------------------------------------------------------------------
    public List<TimeTracking> getTimeTrackingEntriesForUserAndDate(User user, LocalDateTime start, LocalDateTime end) {
        return timeTrackingRepository.findByUserAndStartTimeBetween(user, start, end);
    }

    public List<AdminTimeTrackDTO> getAllTimeTracksWithUser() {
        // 1) Hole alle TimeTracking-Datensätze inkl. .user per JOIN FETCH:
        List<TimeTracking> all = timeTrackingRepository.findAllWithUser();

        // 2) Mappe jeden Eintrag in ein AdminTimeTrackDTO
        return all.stream()
                .map(AdminTimeTrackDTO::new)  // dank dem Konstruktor, der TimeTracking akzeptiert
                .collect(Collectors.toList());
    }

    /**
     * Updatet einen einzelnen TimeTracking-Datensatz (ID),
     * prüft Admin- und Userpasswort, setzt Start-/Endzeit.
     */
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
                // Bei Self-Edit müssen adminPassword und userPassword gleich sein + beide matchen
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

    /**
     * Prüft, ob z.B. von PunchOrder=4 (WORK_END) nach WORK_START gesprungen werden kann usw.
     */
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

    private int mapStatusToPunchOrder(String newStatus) {
        switch (newStatus) {
            case "WORK_START":  return 1;
            case "BREAK_START": return 2;
            case "BREAK_END":   return 3;
            case "WORK_END":    return 4;
            default:            return 0;
        }
    }

    /**
     * Erzeugt einen neuen Punch-Datensatz mit PunchOrder=1..4,
     * setzt dailyDate (heute) und endTime, wenn WorkEnd(4).
     */
    private TimeTracking createNewPunch(User user, int punchOrder) {
        TimeTracking tt = new TimeTracking();
        tt.setUser(user);
        tt.setPunchOrder(punchOrder);

        LocalDateTime now = LocalDateTime.now();
        tt.setStartTime(now);

        // Setze dailyDate = heutiges Datum
        tt.setDailyDate(now.toLocalDate());

        // Bei WORK_END (4) sofort endTime=now
        if (punchOrder == 4) {
            tt.setEndTime(now);
        }
        tt.setCorrected(false);

        TimeTracking saved = timeTrackingRepository.save(tt);
        logger.info("createNewPunch: Neuer Punch mit Order {} für '{}' um {}", punchOrder, user.getUsername(), now);
        return saved;
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
