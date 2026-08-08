package com.chrono.chrono.controller;

import com.chrono.chrono.repositories.ScheduleEntryRepository;
import com.chrono.chrono.repositories.ScheduleChangeLogRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.ScheduleRuleRepository;
import com.chrono.chrono.dto.ScheduleChangeLogDTO;
import com.chrono.chrono.dto.ScheduleEntryDTO;
import com.chrono.chrono.entities.ScheduleChangeLog;
import com.chrono.chrono.entities.ScheduleEntry;
import com.chrono.chrono.entities.ScheduleRule;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.AccessControlService;
import com.chrono.chrono.services.WorkScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/schedule")
public class AdminScheduleEntryController {

    @Autowired
    private ScheduleEntryRepository entryRepo;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private ScheduleRuleRepository ruleRepo;
    @Autowired
    private WorkScheduleService workScheduleService;
    @Autowired
    private AccessControlService accessControlService;
    @Autowired
    private ScheduleChangeLogRepository scheduleChangeLogRepository;

    private Long requireCompanyIdForSchedule(User actor) {
        if (accessControlService.isSuperAdmin(actor)) {
            return null;
        }
        if (actor == null || actor.getCompany() == null || actor.getCompany().getId() == null) {
            throw new AccessDeniedException("Company scoped schedule access required");
        }
        return actor.getCompany().getId();
    }

    private void requireScheduleUserAccess(User actor, User target) {
        if (accessControlService.isSuperAdmin(actor)) {
            return;
        }
        if (!accessControlService.sameCompany(actor, target)) {
            throw new AccessDeniedException("Not allowed to manage this schedule entry");
        }
    }

    private String displayName(User user) {
        if (user == null) {
            return "";
        }
        String firstName = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String lastName = user.getLastName() != null ? user.getLastName().trim() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? user.getUsername() : fullName;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 2000) {
            return value;
        }
        return value.substring(0, 1997) + "...";
    }

    private String entrySummary(User user, LocalDate date, String shift) {
        return displayName(user) + " / " + date + " / " + shift;
    }

    private void recordScheduleChange(User actor, ScheduleEntry entry, String action, String details) {
        if (actor == null || entry == null || entry.getDate() == null || action == null || action.isBlank()) {
            return;
        }
        User target = entry.getUser();
        ScheduleChangeLog log = new ScheduleChangeLog();
        log.setActor(actor);
        log.setTargetUser(target);
        log.setCompany(target != null && target.getCompany() != null ? target.getCompany() : actor.getCompany());
        log.setScheduleEntryId(entry.getId());
        log.setActorUsername(actor.getUsername());
        log.setActorDisplayName(displayName(actor));
        log.setTargetUsername(target != null ? target.getUsername() : null);
        log.setTargetDisplayName(displayName(target));
        log.setAction(action);
        log.setScheduleDate(entry.getDate());
        log.setShift(entry.getShift());
        log.setDetails(truncate(details));
        scheduleChangeLogRepository.save(log);
    }

    private int getShiftDurationMinutes(String shiftKey) {
        return ruleRepo.findByShiftKey(shiftKey)
                .map(rule -> {
                    LocalTime start = LocalTime.parse(rule.getStartTime());
                    LocalTime end = LocalTime.parse(rule.getEndTime());
                    long minutes = Duration.between(start, end).toMinutes();
                    if (minutes <= 0) {
                        minutes += Duration.ofHours(24).toMinutes();
                    }
                    return (int) minutes;
                })
                .orElse(0);
    }

    private int toMinuteOfDay(String time) {
        LocalTime parsed = LocalTime.parse(time);
        return (parsed.getHour() * 60) + parsed.getMinute();
    }

    private List<int[]> getShiftIntervals(String shiftKey) {
        Optional<ScheduleRule> ruleOpt = ruleRepo.findByShiftKey(shiftKey);
        if (ruleOpt.isEmpty()) {
            return List.of();
        }
        ScheduleRule rule = ruleOpt.get();
        int start = toMinuteOfDay(rule.getStartTime());
        int end = toMinuteOfDay(rule.getEndTime());
        if (end > start) {
            return List.of(new int[] { start, end });
        }
        if (end < start) {
            List<int[]> intervals = new ArrayList<>();
            intervals.add(new int[] { start, 24 * 60 });
            intervals.add(new int[] { 0, end });
            return intervals;
        }
        return List.of();
    }

    private boolean shiftsOverlap(String leftShift, String rightShift) {
        List<int[]> leftIntervals = getShiftIntervals(leftShift);
        List<int[]> rightIntervals = getShiftIntervals(rightShift);
        for (int[] left : leftIntervals) {
            for (int[] right : rightIntervals) {
                if (Math.max(left[0], right[0]) < Math.min(left[1], right[1])) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getExpectedDailyMinutes(User user, LocalDate date) {
        return Math.max(0, (int) Math.round(workScheduleService.getExpectedWorkHours(user, date) * 60));
    }

    private int sumShiftMinutes(List<ScheduleEntry> entries) {
        return entries.stream()
                .mapToInt(entry -> getShiftDurationMinutes(entry.getShift()))
                .sum();
    }

    private List<ScheduleEntry> getDayEntriesExcluding(User user, LocalDate date, Long excludedEntryId) {
        return entryRepo.findAllByUserAndDate(user, date).stream()
                .filter(entry -> excludedEntryId == null || !entry.getId().equals(excludedEntryId))
                .toList();
    }

    private String validateAssignment(User user, LocalDate date, String shiftKey, Long excludedEntryId) {
        if (date == null || shiftKey == null || shiftKey.isBlank()) {
            return "Date and shift are required";
        }
        int newEntryMinutes = getShiftDurationMinutes(shiftKey);
        if (newEntryMinutes <= 0) {
            return "Shift is invalid";
        }
        if (workScheduleService.isDayOff(user, date) || getExpectedDailyMinutes(user, date) <= 0) {
            return "User is not scheduled to work on this day";
        }

        List<ScheduleEntry> dayEntries = getDayEntriesExcluding(user, date, excludedEntryId);
        boolean sameShiftExists = dayEntries.stream().anyMatch(entry -> shiftKey.equals(entry.getShift()));
        if (sameShiftExists) {
            return "User already scheduled for this shift";
        }
        boolean overlaps = dayEntries.stream().anyMatch(entry -> shiftsOverlap(shiftKey, entry.getShift()));
        if (overlaps) {
            return "Shift overlaps with another shift for this user";
        }

        int existingDayMinutes = sumShiftMinutes(dayEntries);
        int maxDayMinutes = getExpectedDailyMinutes(user, date);
        if (existingDayMinutes + newEntryMinutes > maxDayMinutes) {
            return "Daily work hours limit exceeded";
        }

        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<ScheduleEntry> weekEntries = entryRepo.findByUserAndDateBetween(user, weekStart, weekEnd).stream()
                .filter(entry -> excludedEntryId == null || !entry.getId().equals(excludedEntryId))
                .toList();
        int existingWeekMinutes = sumShiftMinutes(weekEntries);
        int maxWeekMinutes = workScheduleService.getExpectedWeeklyMinutes(user, date);
        if (existingWeekMinutes + newEntryMinutes > maxWeekMinutes) {
            return "Weekly work hours limit exceeded";
        }

        return null;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ScheduleEntryDTO> getEntries(@RequestParam String start, @RequestParam String end, Principal principal) {
        User actor = accessControlService.requireAuthenticatedUser(principal);
        LocalDate s = LocalDate.parse(start);
        LocalDate e = LocalDate.parse(end);
        List<ScheduleEntry> entries = accessControlService.isSuperAdmin(actor)
                ? entryRepo.findByDateBetween(s, e)
                : entryRepo.findByUser_Company_IdAndDateBetween(
                    requireCompanyIdForSchedule(actor), s, e);
        return entries.stream().map(ScheduleEntryDTO::new).toList();
    }

    @GetMapping("/logs")
    @PreAuthorize("isAuthenticated()")
    public List<ScheduleChangeLogDTO> getChangeLogs(@RequestParam String start, @RequestParam String end, Principal principal) {
        User actor = accessControlService.requireAuthenticatedUser(principal);
        if (!accessControlService.isAdmin(actor)) {
            throw new AccessDeniedException("Schedule change log is admin only");
        }
        LocalDate s = LocalDate.parse(start);
        LocalDate e = LocalDate.parse(end);
        List<ScheduleChangeLog> logs = accessControlService.isSuperAdmin(actor)
                ? scheduleChangeLogRepository.findByScheduleDateBetweenOrderByCreatedAtDesc(s, e)
                : scheduleChangeLogRepository.findByCompany_IdAndScheduleDateBetweenOrderByCreatedAtDesc(
                    requireCompanyIdForSchedule(actor), s, e);
        return logs.stream().map(ScheduleChangeLogDTO::new).toList();
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> saveEntry(@RequestBody ScheduleEntryDTO dto, Principal principal) {
        User actor = accessControlService.requireAuthenticatedUser(principal);
        if (dto.getUserId() == null || dto.getShift() == null || dto.getShift().isEmpty()) {
            return ResponseEntity.badRequest().body("User ID and Shift are required.");
        }

        Optional<User> userOpt = userRepo.findById(dto.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOpt.get();
        requireScheduleUserAccess(actor, user);

        // Update existing entry if ID is provided
        if (dto.getId() != null) {
            Optional<ScheduleEntry> entryOpt = entryRepo.findById(dto.getId());
            if (entryOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Entry not found");
            }
            ScheduleEntry entry = entryOpt.get();
            requireScheduleUserAccess(actor, entry.getUser());

            String validationError = validateAssignment(user, dto.getDate(), dto.getShift(), entry.getId());
            if (validationError != null) {
                return ResponseEntity.badRequest().body(validationError);
            }

            String before = entrySummary(entry.getUser(), entry.getDate(), entry.getShift());
            entry.setUser(user);
            entry.setDate(dto.getDate());
            entry.setShift(dto.getShift());
            entry.setNote(dto.getNote());
            entryRepo.save(entry);
            recordScheduleChange(actor, entry, "UPDATE", "Von " + before + " zu " + entrySummary(user, dto.getDate(), dto.getShift()));
            return ResponseEntity.ok(new ScheduleEntryDTO(entry));
        }

        Optional<ScheduleEntry> existingEntry = entryRepo.findByUserAndDateAndShift(user, dto.getDate(), dto.getShift());
        if(existingEntry.isPresent()) {
            return ResponseEntity.ok(new ScheduleEntryDTO(existingEntry.get()));
        }

        String validationError = validateAssignment(user, dto.getDate(), dto.getShift(), null);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }

        ScheduleEntry newEntry = new ScheduleEntry();
        newEntry.setUser(user);
        newEntry.setDate(dto.getDate());
        newEntry.setShift(dto.getShift());
        newEntry.setNote(dto.getNote());

        entryRepo.save(newEntry);
        recordScheduleChange(actor, newEntry, "CREATE", "Schicht erstellt: " + entrySummary(user, dto.getDate(), dto.getShift()));
        return ResponseEntity.ok(new ScheduleEntryDTO(newEntry));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateEntry(@PathVariable Long id, @RequestBody ScheduleEntryDTO dto, Principal principal) {
        dto.setId(id);
        return saveEntry(dto, principal);
    }

    @PostMapping("/autofill")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> autoFillSchedule(@RequestBody List<ScheduleEntryDTO> entries, Principal principal) {
        User actor = accessControlService.requireAuthenticatedUser(principal);
        Map<String, Integer> weeklyMinutes = new HashMap<>();
        Map<String, Integer> dailyMinutes = new HashMap<>();
        Map<String, List<String>> plannedShiftKeys = new HashMap<>();
        List<ScheduleEntry> newEntries = new ArrayList<>();

        for (ScheduleEntryDTO dto : entries) {
            if (dto == null || dto.getUserId() == null || dto.getDate() == null || dto.getShift() == null || dto.getShift().isBlank()) {
                continue;
            }
            User user = userRepo.findById(dto.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            try {
                requireScheduleUserAccess(actor, user);
            } catch (RuntimeException ex) {
                continue;
            }

            int newMinutes = getShiftDurationMinutes(dto.getShift());
            if (newMinutes <= 0 || workScheduleService.isDayOff(user, dto.getDate()) || getExpectedDailyMinutes(user, dto.getDate()) <= 0) {
                continue;
            }

            String dayKey = user.getId() + ":" + dto.getDate();
            List<String> shiftKeysForDay = plannedShiftKeys.computeIfAbsent(dayKey, key ->
                    entryRepo.findAllByUserAndDate(user, dto.getDate()).stream()
                            .map(ScheduleEntry::getShift)
                            .collect(Collectors.toCollection(ArrayList::new))
            );
            boolean dayConflict = shiftKeysForDay.stream()
                    .anyMatch(existingShift -> dto.getShift().equals(existingShift) || shiftsOverlap(dto.getShift(), existingShift));
            if (dayConflict) {
                continue;
            }

            int currentDayMinutes = dailyMinutes.computeIfAbsent(dayKey, key ->
                    sumShiftMinutes(entryRepo.findAllByUserAndDate(user, dto.getDate()))
            );
            int maxDayMinutes = getExpectedDailyMinutes(user, dto.getDate());
            if (currentDayMinutes + newMinutes > maxDayMinutes) {
                continue;
            }

            LocalDate weekStart = dto.getDate().with(DayOfWeek.MONDAY);
            String weekKey = user.getId() + ":" + weekStart;
            int currentWeekMinutes = weeklyMinutes.computeIfAbsent(weekKey, k -> {
                List<ScheduleEntry> weekEntries = entryRepo.findByUserAndDateBetween(user, weekStart, weekStart.plusDays(6));
                return sumShiftMinutes(weekEntries);
            });
            int max = workScheduleService.getExpectedWeeklyMinutes(user, dto.getDate());
            if (currentWeekMinutes + newMinutes > max) {
                continue;
            }
            dailyMinutes.put(dayKey, currentDayMinutes + newMinutes);
            weeklyMinutes.put(weekKey, currentWeekMinutes + newMinutes);
            shiftKeysForDay.add(dto.getShift());

            ScheduleEntry newEntry = new ScheduleEntry();
            newEntry.setUser(user);
            newEntry.setDate(dto.getDate());
            newEntry.setShift(dto.getShift());
            newEntries.add(newEntry);
        }

        List<ScheduleEntry> savedEntries = entryRepo.saveAll(newEntries);
        savedEntries.forEach(entry -> recordScheduleChange(
                actor,
                entry,
                "AUTOFILL_CREATE",
                "Automatisch eingetragen: " + entrySummary(entry.getUser(), entry.getDate(), entry.getShift())
        ));
        return ResponseEntity.ok(Map.of(
                "created", savedEntries.size(),
                "entries", savedEntries.stream().map(ScheduleEntryDTO::new).toList()
        ));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteEntries(@RequestBody List<Long> ids, Principal principal) {
        User actor = accessControlService.requireAuthenticatedUser(principal);
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(Map.of("deleted", 0));
        }

        int deleted = 0;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            Optional<ScheduleEntry> entry = entryRepo.findById(id);
            if (entry.isEmpty()) {
                continue;
            }
            requireScheduleUserAccess(actor, entry.get().getUser());
            recordScheduleChange(
                    actor,
                    entry.get(),
                    "BULK_DELETE",
                    "Schicht entfernt: " + entrySummary(entry.get().getUser(), entry.get().getDate(), entry.get().getShift())
            );
            entryRepo.delete(entry.get());
            deleted++;
        }

        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @PostMapping("/copy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> copySchedule(@RequestBody List<ScheduleEntryDTO> entries, Principal principal) {
        User actor = accessControlService.requireAuthenticatedUser(principal);
        if (entries == null || entries.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        LocalDate firstDate = entries.get(0).getDate();
        LocalDate weekStart = firstDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<ScheduleEntry> existingEntries = accessControlService.isSuperAdmin(actor)
                ? entryRepo.findByDateBetween(weekStart, weekEnd)
                : entryRepo.findByUser_Company_IdAndDateBetween(
                    requireCompanyIdForSchedule(actor), weekStart, weekEnd);
        if (!existingEntries.isEmpty()) {
            existingEntries.forEach(entry -> recordScheduleChange(
                    actor,
                    entry,
                    "COPY_DELETE",
                    "Beim Einfuegen ersetzt: " + entrySummary(entry.getUser(), entry.getDate(), entry.getShift())
            ));
            entryRepo.deleteAll(existingEntries);
        }

        List<ScheduleEntry> newEntriesToSave = entries.stream()
                .map(dto -> {
                    User user = userRepo.findById(dto.getUserId()).orElse(null);
                    if (user == null) {
                        return null;
                    }
                    try {
                        requireScheduleUserAccess(actor, user);
                    } catch (RuntimeException ex) {
                        return null;
                    }
                    ScheduleEntry newEntry = new ScheduleEntry();
                    newEntry.setUser(user);
                    newEntry.setDate(dto.getDate());
                    newEntry.setShift(dto.getShift());
                    return newEntry;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<ScheduleEntry> savedEntries = entryRepo.saveAll(newEntriesToSave);
        savedEntries.forEach(entry -> recordScheduleChange(
                actor,
                entry,
                "COPY_CREATE",
                "Aus kopierter Woche eingefuegt: " + entrySummary(entry.getUser(), entry.getDate(), entry.getShift())
        ));

        return ResponseEntity.ok().build();
    }


    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteEntry(@PathVariable Long id, Principal principal) {
        User actor = accessControlService.requireAuthenticatedUser(principal);
        Optional<ScheduleEntry> entry = entryRepo.findById(id);
        if (entry.isEmpty()) {
            return ResponseEntity.badRequest().body("Entry not found");
        }
        requireScheduleUserAccess(actor, entry.get().getUser());
        recordScheduleChange(
                actor,
                entry.get(),
                "DELETE",
                "Schicht geloescht: " + entrySummary(entry.get().getUser(), entry.get().getDate(), entry.get().getShift())
        );
        entryRepo.deleteById(id);
        return ResponseEntity.ok("deleted");
    }
}
