package com.chrono.chrono.controller;

import com.chrono.chrono.repositories.ScheduleEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.ScheduleRuleRepository;
import com.chrono.chrono.dto.ScheduleEntryDTO;
import com.chrono.chrono.entities.ScheduleEntry;
import com.chrono.chrono.entities.ScheduleRule;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.AccessControlService;
import com.chrono.chrono.services.WorkScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public List<ScheduleEntryDTO> getEntries(@RequestParam String start, @RequestParam String end, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        LocalDate s = LocalDate.parse(start);
        LocalDate e = LocalDate.parse(end);
        List<ScheduleEntry> entries = accessControlService.isSuperAdmin(admin)
                ? entryRepo.findByDateBetween(s, e)
                : entryRepo.findByUser_Company_IdAndDateBetween(
                    accessControlService.requireCompanyIdForTenantAdmin(admin), s, e);
        return entries.stream().map(ScheduleEntryDTO::new).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> saveEntry(@RequestBody ScheduleEntryDTO dto, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        if (dto.getUserId() == null || dto.getShift() == null || dto.getShift().isEmpty()) {
            return ResponseEntity.badRequest().body("User ID and Shift are required.");
        }

        Optional<User> userOpt = userRepo.findById(dto.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOpt.get();
        accessControlService.requireCanManageUser(admin, user);

        // Update existing entry if ID is provided
        if (dto.getId() != null) {
            Optional<ScheduleEntry> entryOpt = entryRepo.findById(dto.getId());
            if (entryOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Entry not found");
            }
            ScheduleEntry entry = entryOpt.get();
            accessControlService.requireCanManageUser(admin, entry.getUser());

            String validationError = validateAssignment(user, dto.getDate(), dto.getShift(), entry.getId());
            if (validationError != null) {
                return ResponseEntity.badRequest().body(validationError);
            }

            entry.setDate(dto.getDate());
            entry.setShift(dto.getShift());
            entry.setNote(dto.getNote());
            entryRepo.save(entry);
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
        return ResponseEntity.ok(new ScheduleEntryDTO(newEntry));
    }

    @PostMapping("/autofill")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> autoFillSchedule(@RequestBody List<ScheduleEntryDTO> entries, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
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
                accessControlService.requireCanManageUser(admin, user);
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
        return ResponseEntity.ok(Map.of(
                "created", savedEntries.size(),
                "entries", savedEntries.stream().map(ScheduleEntryDTO::new).toList()
        ));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> deleteEntries(@RequestBody List<Long> ids, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(Map.of("deleted", 0));
        }

        int deleted = 0;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            Optional<ScheduleEntry> entry = entryRepo.findById(id);
            if (entry.isEmpty()) {
                continue;
            }
            accessControlService.requireCanManageUser(admin, entry.get().getUser());
            entryRepo.delete(entry.get());
            deleted++;
        }

        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @PostMapping("/copy")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> copySchedule(@RequestBody List<ScheduleEntryDTO> entries, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        if (entries == null || entries.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        LocalDate firstDate = entries.get(0).getDate();
        LocalDate weekStart = firstDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<ScheduleEntry> existingEntries = accessControlService.isSuperAdmin(admin)
                ? entryRepo.findByDateBetween(weekStart, weekEnd)
                : entryRepo.findByUser_Company_IdAndDateBetween(
                    accessControlService.requireCompanyIdForTenantAdmin(admin), weekStart, weekEnd);
        if (!existingEntries.isEmpty()) {
            entryRepo.deleteAll(existingEntries);
        }

        List<ScheduleEntry> newEntriesToSave = entries.stream()
                .map(dto -> {
                    User user = userRepo.findById(dto.getUserId()).orElse(null);
                    if (user == null) {
                        return null;
                    }
                    try {
                        accessControlService.requireCanManageUser(admin, user);
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

        entryRepo.saveAll(newEntriesToSave);

        return ResponseEntity.ok().build();
    }


    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> deleteEntry(@PathVariable Long id, Principal principal) {
        User admin = accessControlService.requireAuthenticatedUser(principal);
        Optional<ScheduleEntry> entry = entryRepo.findById(id);
        if (entry.isEmpty()) {
            return ResponseEntity.badRequest().body("Entry not found");
        }
        accessControlService.requireCanManageUser(admin, entry.get().getUser());
        entryRepo.deleteById(id);
        return ResponseEntity.ok("deleted");
    }
}
