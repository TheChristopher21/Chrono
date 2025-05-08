package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.TimeTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/admin/timetracking")
public class AdminTimeTrackingController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TimeTrackingService timeTrackingService;

    @GetMapping("/all")
    public ResponseEntity<?> getAllTimeTracks(Principal principal) {
        try {
            Long adminCompanyId = userRepository
                    .findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"))
                    .getCompany()
                    .getId();

            // Hier: 'dtos' ist eine List<AdminTimeTrackDTO>
            List<AdminTimeTrackDTO> dtos = timeTrackingService.getAllTimeTracksWithUser();

            // Filtern nach der Company des Admins
            List<AdminTimeTrackDTO> filtered = dtos.stream()
                    .filter(dto -> dto.getCompanyId() != null
                            && dto.getCompanyId().equals(adminCompanyId))
                    .toList();

            return ResponseEntity.ok(filtered);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }



    @GetMapping("/admin/weekly-balance")
    public ResponseEntity<?> getAdminWeeklyBalances(@RequestParam String monday) {
        LocalDate mondayDate = LocalDate.parse(monday);
        List<User> allUsers = userRepository.findAll();

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : allUsers) {
            int weekly = timeTrackingService.getWeeklyBalance(u, mondayDate);
            Map<String, Object> entry = new HashMap<>();
            entry.put("username", u.getUsername());
            entry.put("weeklyBalance", weekly);
            entry.put("color", u.getColor());
            result.add(entry);
        }

        return ResponseEntity.ok(result);
    }
    @GetMapping("/admin/tracking-balances")
    public ResponseEntity<?> getAllCurrentTrackingBalances() {
        List<User> users = userRepository.findAll();

        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("username", u.getUsername());
            entry.put("trackingBalance", u.getTrackingBalanceInMinutes());
            entry.put("color", u.getColor());
            return entry;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PutMapping("/editDay")
    public ResponseEntity<?> editDayTimeEntries(
            @RequestParam String targetUsername,
            @RequestParam String date,
            @RequestParam String workStart,
            @RequestParam String breakStart,
            @RequestParam String breakEnd,
            @RequestParam String workEnd,
            @RequestParam String adminUsername,
            @RequestParam String adminPassword,
            @RequestParam String userPassword) {

        try {
            String result = timeTrackingService.updateDayTimeEntries(
                    targetUsername,
                    date,
                    workStart,
                    breakStart,
                    breakEnd,
                    workEnd,
                    adminUsername,
                    adminPassword,
                    userPassword);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
