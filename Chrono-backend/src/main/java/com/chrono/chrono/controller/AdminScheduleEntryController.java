package com.chrono.chrono.controller;

import com.chrono.chrono.repositories.ScheduleEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.ScheduleRuleRepository;
import com.chrono.chrono.dto.ScheduleEntryDTO;
import com.chrono.chrono.entities.ScheduleEntry;
import com.chrono.chrono.entities.ScheduleRule;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.WorkScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
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

    private int getShiftDurationMinutes(String shiftKey) {
        return ruleRepo.findByShiftKey(shiftKey)
                .map(rule -> {
                    LocalTime start = LocalTime.parse(rule.getStartTime());
                    LocalTime end = LocalTime.parse(rule.getEndTime());
                    return (int) Duration.between(start, end).toMinutes();
                })
                .orElse(0);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public List<ScheduleEntryDTO> getEntries(@RequestParam String start, @RequestParam String end) {
        LocalDate s = LocalDate.parse(start);
        LocalDate e = LocalDate.parse(end);
        return entryRepo.findByDateBetween(s, e).stream().map(ScheduleEntryDTO::new).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> saveEntry(@RequestBody ScheduleEntryDTO dto) {
        if (dto.getUserId() == null || dto.getShift() == null || dto.getShift().isEmpty()) {
            return ResponseEntity.badRequest().body("User ID and Shift are required.");
        }

        Optional<User> userOpt = userRepo.findById(dto.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOpt.get();

        if (workScheduleService.isDayOff(user, dto.getDate())
                || workScheduleService.getExpectedWorkHours(user, dto.getDate()) <= 0) {
            return ResponseEntity.badRequest().body("User is not scheduled to work on this day");
        }

        // Update existing entry if ID is provided
        if (dto.getId() != null) {
            Optional<ScheduleEntry> entryOpt = entryRepo.findById(dto.getId());
            if (entryOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Entry not found");
            }
            ScheduleEntry entry = entryOpt.get();

            Optional<ScheduleEntry> conflict = entryRepo.findByUserAndDate(user, dto.getDate());
            if (conflict.isPresent() && !conflict.get().getId().equals(entry.getId())) {
                return ResponseEntity.badRequest().body("User already scheduled to work on this day");
            }

            LocalDate weekStart = dto.getDate().with(DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            List<ScheduleEntry> weekEntries = entryRepo.findByUserAndDateBetween(user, weekStart, weekEnd);
            int existingMinutes = weekEntries.stream()
                    .filter(e -> !e.getId().equals(entry.getId()))
                    .mapToInt(e -> getShiftDurationMinutes(e.getShift()))
                    .sum();
            int newEntryMinutes = getShiftDurationMinutes(dto.getShift());
            int maxMinutes = workScheduleService.getExpectedWeeklyMinutes(user, dto.getDate());
            if (existingMinutes + newEntryMinutes > maxMinutes) {
                return ResponseEntity.badRequest().body("Weekly work hours limit exceeded");
            }

            entry.setDate(dto.getDate());
            entry.setShift(dto.getShift());
            entry.setNote(dto.getNote());
            entryRepo.save(entry);
            return ResponseEntity.ok(new ScheduleEntryDTO(entry));
        }

        Optional<ScheduleEntry> existingEntry = entryRepo.findByUserAndDate(user, dto.getDate());
        if(existingEntry.isPresent()) {
            return ResponseEntity.ok(new ScheduleEntryDTO(existingEntry.get()));
        }

        LocalDate weekStart = dto.getDate().with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<ScheduleEntry> weekEntries = entryRepo.findByUserAndDateBetween(user, weekStart, weekEnd);
        int existingMinutes = weekEntries.stream().mapToInt(e -> getShiftDurationMinutes(e.getShift())).sum();
        int newEntryMinutes = getShiftDurationMinutes(dto.getShift());
        int maxMinutes = workScheduleService.getExpectedWeeklyMinutes(user, dto.getDate());
        if (existingMinutes + newEntryMinutes > maxMinutes) {
            return ResponseEntity.badRequest().body("Weekly work hours limit exceeded");
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
    public ResponseEntity<?> autoFillSchedule(@RequestBody List<ScheduleEntryDTO> entries) {
        Map<String, Integer> weeklyMinutes = new HashMap<>();
        List<ScheduleEntry> newEntries = entries.stream().map(dto -> {
            User user = userRepo.findById(dto.getUserId()).orElse(null);
            if (user == null) {
                return null;
            }
            if (workScheduleService.isDayOff(user, dto.getDate())
                    || workScheduleService.getExpectedWorkHours(user, dto.getDate()) <= 0) {
                return null;
            }
            Optional<ScheduleEntry> existing = entryRepo.findByUserAndDate(user, dto.getDate());
            if (existing.isPresent()) {
                return null;
            }
            LocalDate weekStart = dto.getDate().with(DayOfWeek.MONDAY);
            String key = user.getId() + ":" + weekStart;
            int current = weeklyMinutes.computeIfAbsent(key, k -> {
                List<ScheduleEntry> weekEntries = entryRepo.findByUserAndDateBetween(user, weekStart, weekStart.plusDays(6));
                return weekEntries.stream().mapToInt(e -> getShiftDurationMinutes(e.getShift())).sum();
            });
            int newMinutes = getShiftDurationMinutes(dto.getShift());
            int max = workScheduleService.getExpectedWeeklyMinutes(user, dto.getDate());
            if (current + newMinutes > max) {
                return null;
            }
            weeklyMinutes.put(key, current + newMinutes);

            ScheduleEntry newEntry = new ScheduleEntry();
            newEntry.setUser(user);
            newEntry.setDate(dto.getDate());
            newEntry.setShift(dto.getShift());
            return newEntry;
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());

        entryRepo.saveAll(newEntries);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/copy")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<?> copySchedule(@RequestBody List<ScheduleEntryDTO> entries) {
        if (entries == null || entries.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        LocalDate firstDate = entries.get(0).getDate();
        LocalDate weekStart = firstDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<ScheduleEntry> existingEntries = entryRepo.findByDateBetween(weekStart, weekEnd);
        if (!existingEntries.isEmpty()) {
            entryRepo.deleteAll(existingEntries);
        }

        List<ScheduleEntry> newEntriesToSave = entries.stream()
                .map(dto -> {
                    User user = userRepo.findById(dto.getUserId()).orElse(null);
                    if (user == null) {
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
    public ResponseEntity<?> deleteEntry(@PathVariable Long id) {
        if (!entryRepo.existsById(id)) {
            return ResponseEntity.badRequest().body("Entry not found");
        }
        entryRepo.deleteById(id);
        return ResponseEntity.ok("deleted");
    }
}