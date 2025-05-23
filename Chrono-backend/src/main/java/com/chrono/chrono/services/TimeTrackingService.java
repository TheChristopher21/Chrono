package com.chrono.chrono.services;

import com.chrono.chrono.dto.TimeReportDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimeTrackingService {
    @Autowired private TimeTrackingRepository timeTrackingRepo;
    @Autowired private WorkScheduleService  workScheduleService;
    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Beispiel: 8h30 = 510 Minuten
    public static final int FULL_DAY_MINUTES  = 8 * 60 + 30;   // 8 h 30 m
    public static final int FULL_WEEK_MINUTES = FULL_DAY_MINUTES * 5; // 42 h 30 m

    // Hilfsfunktion zum Laden eines Users oder Exception
    private User loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }

    // Holt oder erzeugt die Zeile (TimeTracking) für einen bestimmten Tag
    @Transactional
    public TimeTracking getOrCreateRow(User user, LocalDate date) {
        return timeTrackingRepository.findByUserAndDailyDate(user, date)
                .orElseGet(() -> {
                    TimeTracking tt = new TimeTracking();
                    tt.setUser(user);
                    tt.setDailyDate(date);
                    tt.setCorrected(false);
                    // Hier kann man auch default-Werte für z.B. dailyNote setzen
                    return timeTrackingRepository.save(tt);
                });
    }

    @Transactional
    public TimeTracking getOrCreateTodayRow(User user) {
        return getOrCreateRow(user, LocalDate.now());
    }

    // -------------------------------------------------------------------------
    // SMART PUNCH: 1->2->3->4
    // -------------------------------------------------------------------------
    @Transactional
    public TimeTrackingResponse handleSmartPunch(String username) {
        User user = loadUserByUsername(username);
        TimeTracking row = getOrCreateTodayRow(user);

        if (row.getWorkStart() == null) {
            row.setWorkStart(LocalTime.now());
        } else if (row.getBreakStart() == null) {
            row.setBreakStart(LocalTime.now());
        } else if (row.getBreakEnd() == null) {
            row.setBreakEnd(LocalTime.now());
        } else if (row.getWorkEnd() == null) {
            row.setWorkEnd(LocalTime.now());
        } else {
            // Wenn alles belegt ist, passiert nichts mehr
        }
        row.setCorrected(false);
        timeTrackingRepository.save(row);

        return convertToResponse(row);
    }

    // -------------------------------------------------------------------------
    // Direkte Punch-Methoden: WORK_START, BREAK_START, BREAK_END, WORK_END
    // -------------------------------------------------------------------------
    @Transactional
    public TimeTrackingResponse handlePunch(String username, String punchType) {
        User user = loadUserByUsername(username);
        TimeTracking row = getOrCreateTodayRow(user);

        LocalTime now = LocalTime.now();

        switch (punchType) {
            case "WORK_START" -> {
                if (row.getWorkStart() == null) row.setWorkStart(now);
            }
            case "BREAK_START" -> {
                if (row.getBreakStart() == null) row.setBreakStart(now);
            }
            case "BREAK_END" -> {
                if (row.getBreakEnd() == null) row.setBreakEnd(now);
            }
            case "WORK_END" -> {
                if (row.getWorkEnd() == null) row.setWorkEnd(now);
            }
        }
        row.setCorrected(false);
        timeTrackingRepository.save(row);

        return convertToResponse(row);
    }

    // -------------------------------------------------------------------------
    // HISTORY: Liste aller Tage, absteigend
    // -------------------------------------------------------------------------
    public List<TimeTrackingResponse> getUserHistory(String username) {
        User user = loadUserByUsername(username);
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByDailyDateDesc(user);

        return list.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // DAILY NOTE
    // -------------------------------------------------------------------------
    @Transactional
    public TimeTrackingResponse updateDailyNote(String username, String dateStr, String note) {
        User user = loadUserByUsername(username);
        LocalDate date = LocalDate.parse(dateStr);

        TimeTracking row = getOrCreateRow(user, date);
        row.setDailyNote(note);
        row.setCorrected(true);
        timeTrackingRepository.save(row);

        return convertToResponse(row);
    }

    // -------------------------------------------------------------------------
    // ADMIN: gesamten Tag updaten (WORK_START, BREAK_START, BREAK_END, WORK_END)
    // -------------------------------------------------------------------------
    @Transactional
    public String updateDayTimeEntries(
            String targetUsername,
            String date,
            String workStart,
            String breakStart,
            String breakEnd,
            String workEnd,
            String adminUsername,
            String adminPassword,
            String userPassword
    ) {
        User targetUser = loadUserByUsername(targetUsername);

        checkAdminAndUserPasswords(targetUser, adminUsername, adminPassword, userPassword);

        LocalDate day = LocalDate.parse(date);
        TimeTracking row = getOrCreateRow(targetUser, day);

        // Falls "00:00" => man kann interpretieren als "keine Pause"
        LocalTime ws = LocalTime.parse(workStart);
        LocalTime bs = breakStart.equals("00:00") ? null : LocalTime.parse(breakStart);
        LocalTime be = breakEnd.equals("00:00")   ? null : LocalTime.parse(breakEnd);
        LocalTime we = LocalTime.parse(workEnd);

        row.setWorkStart(ws);
        row.setBreakStart(bs);
        row.setBreakEnd(be);
        row.setWorkEnd(we);
        row.setCorrected(true);

        timeTrackingRepository.save(row);

        // Anschließend Salden neu berechnen (optional)
        rebuildUserBalance(targetUser);

        return "Day entries updated successfully";
    }

    // -------------------------------------------------------------------------
    // ADMIN: einzelnes TimeTracking updaten (z.B. Start-/Endzeit)
    // -------------------------------------------------------------------------
    @Transactional
    public TimeTrackingResponse updateTimeTrackEntry(
            Long id,
            LocalDateTime newStart,
            LocalDateTime newEnd,
            String userPassword,
            String adminUsername,
            String adminPassword
    ) {
        TimeTracking tt = timeTrackingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Time tracking entry not found"));

        User targetUser = tt.getUser();

        checkAdminAndUserPasswords(targetUser, adminUsername, adminPassword, userPassword);

        // Beispiel: hier interpretieren wir newStart = WorkStart,
        //           newEnd   = WorkEnd (Pause manuell?)
        tt.setWorkStart(newStart.toLocalTime());
        tt.setWorkEnd(newEnd.toLocalTime());
        tt.setCorrected(true);

        timeTrackingRepository.save(tt);
        return convertToResponse(tt);
    }

    // -------------------------------------------------------------------------
    // ÜBERSTUNDEN = (IST - SOLL)
    // -------------------------------------------------------------------------

    public int computeDailyWorkDifference(User user, LocalDate date) {

        /* 1) Gearbeitete Minuten (IST) ------------------------------- */
        int workedMinutes = 0;                          // default = 0
        try {
            workedMinutes = getWorkedMinutes(user, date);
        } catch (RuntimeException ex) {
            // kein Eintrag → bleibt 0 Ist-Minuten
        }

        /* 2) Erwartete Minuten (SOLL) ------------------------------- */
        int expectedMinutes = 0;
        if (!Boolean.TRUE.equals(user.getIsHourly())) {     // Stundenlöhner haben kein Soll
            expectedMinutes = workScheduleService
                    .computeExpectedWorkMinutes(user, date);
        }

        /* 3) Saldo zurückgeben -------------------------------------- */
        return workedMinutes - expectedMinutes;
    }




    public int getWeeklyBalance(User user, LocalDate monday) {
        int sum = 0;
        for (int i = 0; i < 7; i++) {
            sum += computeDailyWorkDifference(user, monday.plusDays(i));
        }
        return sum;              // Minuten-Saldo der Woche (positiv = Überstunden)
    }

    private int computeWeeklyWorkDifference(User user, LocalDate weekStart, LocalDate lastDay) {
        int worked = 0;
        LocalDate end = weekStart.plusDays(6);
        for (LocalDate d = weekStart; !d.isAfter(end) && !d.isAfter(lastDay); d = d.plusDays(1)) {
            try {
                worked += getWorkedMinutes(user, d);
            } catch (RuntimeException ex) {
                // kein Eintrag -> 0 Minuten
            }
        }

        double factor = (user.getWorkPercentage() != null)
                ? user.getWorkPercentage() / 100.0
                : 1.0;
        int expected = (int) Math.round(FULL_WEEK_MINUTES * factor);
        return worked - expected;
    }


    public int getWorkedMinutes(User user, LocalDate date) {
        Optional<TimeTracking> ott = timeTrackingRepo                      // eine Zeile pro Tag!
                .findByUserAndDailyDate(user, date);

        if (ott.isPresent()) {
            TimeTracking tt = ott.get();
            int start = toMinutes(tt.getWorkStart());              // 07:45 -> 465 min
            int end = toMinutes(tt.getWorkEnd());                // 17:30 -> 1050 min
            int pause = 0;
            if (tt.getBreakStart() != null && tt.getBreakEnd() != null) {
                pause = toMinutes(tt.getBreakEnd()) - toMinutes(tt.getBreakStart());
            }
            return (end - start) - pause;
        }
        else {
            return 0;
        }

        // effektive Arbeitszeit
    }
    private static int toMinutes(LocalTime t) {
        return (t == null) ? 0 : t.getHour() * 60 + t.getMinute();
    }
    // -------------------------------------------------------------------------
    // ADMIN: Salden Rebuild für einen User
    // -------------------------------------------------------------------------
    /**
     * Baut das Überstunden­konto eines Users komplett neu auf.
     * – berücksichtigt **jeden** Kalendertag vom ersten Eintrag bis heute
     * – Tage ohne Tracking-Zeile zählen als 0 Ist­minuten
     */




    @Transactional
    public void rebuildUserBalance(User user) {

    /* ------------------------------------------------------------------
       0) Alle vorhandenen Tracking-Zeilen holen
          – wir brauchen sie nur, um den Datumsbereich zu kennen.
       ------------------------------------------------------------------ */
        List<TimeTracking> rows = timeTrackingRepository.findByUser(user);

        if (rows.isEmpty()) {                    // User hat noch gar nichts getrackt
            user.setTrackingBalanceInMinutes(0);
            userRepository.save(user);
            return;
        }

    /* ------------------------------------------------------------------
       1) Von ERSTEM Eintrag bis HEUTE jeden Kalendertag durchlaufen
       ------------------------------------------------------------------ */
        LocalDate firstDay = rows.stream()
                .map(TimeTracking::getDailyDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate lastDay  = LocalDate.now();

        int totalMinutes = 0;

        if (Boolean.TRUE.equals(user.getIsPercentage())) {
            for (LocalDate w = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); !w.isAfter(lastDay); w = w.plusWeeks(1)) {
                int diff = computeWeeklyWorkDifference(user, w, lastDay);
                totalMinutes += diff;
            }
        } else {
            for (LocalDate d = firstDay; !d.isAfter(lastDay); d = d.plusDays(1)) {
                int temp = computeDailyWorkDifference(user, d);
                totalMinutes += temp;
                //  ►  Ist-Minuten = 0, wenn kein Eintrag vorhanden
                //  ►  Soll-Minuten kommen immer aus WorkScheduleService
            }
        }

    /* ------------------------------------------------------------------
       2) Speichern
       ------------------------------------------------------------------ */
        user.setTrackingBalanceInMinutes(totalMinutes);
        userRepository.save(user);
    }


    // -------------------------------------------------------------------------
    // ADMIN: Salden Rebuild für alle User
    // -------------------------------------------------------------------------
    @Transactional
    public void rebuildAllUserBalancesOnce() {
        for (User u : userRepository.findAll()) {
            rebuildUserBalance(u);
        }
    }

    // -------------------------------------------------------------------------
    // REPORT im Datumsbereich
    // -------------------------------------------------------------------------
    public List<TimeReportDTO> getReport(String username, String startDate, String endDate) {
        User user = loadUserByUsername(username);
        LocalDate from = LocalDate.parse(startDate);
        LocalDate to   = LocalDate.parse(endDate);

        List<TimeTracking> entries = timeTrackingRepository
                .findByUserAndDailyDateBetweenOrderByDailyDateAsc(user, from, to);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d.M.yyyy", Locale.GERMAN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        List<TimeReportDTO> report = new ArrayList<>();

        for (TimeTracking row : entries) {
            String dayStr = row.getDailyDate().format(dateFormatter);

            String ws = (row.getWorkStart()  != null) ? row.getWorkStart().format(timeFormatter)  : "-";
            String bs = (row.getBreakStart() != null) ? row.getBreakStart().format(timeFormatter) : "-";
            String be = (row.getBreakEnd()   != null) ? row.getBreakEnd().format(timeFormatter)   : "-";
            String we = (row.getWorkEnd()    != null) ? row.getWorkEnd().format(timeFormatter)    : "-";

            report.add(new TimeReportDTO(
                    user.getUsername(),
                    dayStr,
                    ws,
                    bs,
                    be,
                    we,
                    row.getDailyNote()
            ));
        }
        return report;
    }

    // -------------------------------------------------------------------------
    // Helfer zum Passwort-Check
    // -------------------------------------------------------------------------
    private void checkAdminAndUserPasswords(
            User targetUser,
            String adminUsername,
            String adminPassword,
            String userPassword
    ) {
        if (adminUsername != null && !adminUsername.trim().isEmpty()) {
            User adminUser = userRepository.findByUsername(adminUsername)
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            String storedAdminPwd = adminUser.getAdminPassword();
            if (storedAdminPwd == null || storedAdminPwd.trim().isEmpty()) {
                storedAdminPwd = adminUser.getPassword();
            }

            // Self-edit?
            if (adminUser.getId().equals(targetUser.getId())) {
                if (!adminPassword.equals(userPassword)) {
                    throw new RuntimeException("For self-edit, admin and user passwords must match");
                }
                if (!passwordEncoder.matches(adminPassword, storedAdminPwd)) {
                    throw new RuntimeException("Invalid admin password");
                }
                if (!passwordEncoder.matches(userPassword, targetUser.getPassword())) {
                    throw new RuntimeException("Invalid user password");
                }
            } else {
                // Fremd-Bearbeitung
                if (!passwordEncoder.matches(adminPassword, storedAdminPwd)) {
                    throw new RuntimeException("Invalid admin password");
                }
                if (!passwordEncoder.matches(userPassword, targetUser.getPassword())) {
                    throw new RuntimeException("Invalid user password");
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DTO-Konvertierung
    // -------------------------------------------------------------------------
    private TimeTrackingResponse convertToResponse(TimeTracking tt) {
        return new TimeTrackingResponse(
                tt.getId(),
                tt.getDailyDate(),
                tt.getWorkStart(),
                tt.getBreakStart(),
                tt.getBreakEnd(),
                tt.getWorkEnd(),
                tt.getDailyNote(),
                tt.isCorrected()
        );
    }

    // -------------------------------------------------------------------------
    // Admin-Helfer, z.B. fürs Auslesen in /api/admin/timetracking/all
    // -------------------------------------------------------------------------
    public List<TimeTracking> getTimeTrackingRowsForUser(User user) {
        return timeTrackingRepository.findByUserOrderByDailyDateDesc(user);
    }
}
