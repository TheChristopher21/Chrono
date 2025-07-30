package com.chrono.chrono.controller;

import com.chrono.chrono.repositories.ScheduleEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.dto.ScheduleEntryDTO;
import com.chrono.chrono.entities.ScheduleEntry;
import com.chrono.chrono.entities.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
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

        Optional<ScheduleEntry> existingEntry = entryRepo.findByUserAndDateAndShift(user, dto.getDate(), dto.getShift());
        if(existingEntry.isPresent()) {
            return ResponseEntity.ok(new ScheduleEntryDTO(existingEntry.get()));
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
        List<ScheduleEntry> newEntries = entries.stream().map(dto -> {
            User user = userRepo.findById(dto.getUserId()).orElse(null);
            if (user == null) {
                return null;
            }
            Optional<ScheduleEntry> existing = entryRepo.findByUserAndDateAndShift(user, dto.getDate(), dto.getShift());
            if (existing.isPresent()) {
                return null;
            }
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