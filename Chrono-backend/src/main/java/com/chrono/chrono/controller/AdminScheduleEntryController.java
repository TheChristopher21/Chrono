package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ScheduleEntryDTO;
import com.chrono.chrono.entities.ScheduleEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.ScheduleEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
        User user = null;
        if (dto.getUserId() != null) {
            Optional<User> userOpt = userRepo.findById(dto.getUserId());
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            user = userOpt.get();
        }
        ScheduleEntry entry = (dto.getId() != null) ?
                entryRepo.findById(dto.getId()).orElse(new ScheduleEntry()) : new ScheduleEntry();
        entry.setUser(user);
        entry.setDate(dto.getDate());
        entry.setShift(dto.getShift());
        entry.setNote(dto.getNote());
        entryRepo.save(entry);
        return ResponseEntity.ok(new ScheduleEntryDTO(entry));
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
