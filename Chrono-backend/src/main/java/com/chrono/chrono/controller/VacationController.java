// src/main/java/com/chrono/chrono/controller/VacationController.java
package com.chrono.chrono.controller;

import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.services.VacationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/vacation")
public class VacationController {

    @Autowired
    private VacationService vacationService;

    // User beantragt Urlaub
    @PostMapping("/create")
    public VacationRequest createVacation(@RequestParam String username,
                                          @RequestParam String startDate,
                                          @RequestParam String endDate,
                                          @RequestParam(required = false, defaultValue = "false") boolean halfDay) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return vacationService.createVacationRequest(username, start, end, halfDay);
    }

    // Admin erstellt Urlaub – über einen Kalender wird ein Zeitraum ausgewählt
    @PostMapping("/adminCreate")
    public VacationRequest adminCreateVacation(@RequestParam String adminUsername,
                                               @RequestParam String adminPassword,
                                               @RequestParam String username,
                                               @RequestParam String startDate,
                                               @RequestParam String endDate,
                                               @RequestParam(required = false, defaultValue = "false") boolean halfDay) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return vacationService.adminCreateVacation(adminUsername, adminPassword, username, start, end, halfDay);
    }

    // User ruft eigene Urlaubsanträge ab
    @GetMapping("/my")
    public List<VacationRequest> getMyVacations(Principal principal) {
        return vacationService.getUserVacations(principal.getName());
    }

    // Admin ruft alle Urlaubsanträge ab
    @GetMapping("/all")
    public List<VacationRequest> getAllVacations() {
        return vacationService.getAllVacations();
    }

    // Admin: Urlaubsantrag genehmigen
    @PostMapping("/approve/{id}")
    public VacationRequest approve(@PathVariable Long id) {
        return vacationService.approveVacation(id);
    }

    // Admin: Urlaubsantrag ablehnen
    @PostMapping("/deny/{id}")
    public VacationRequest deny(@PathVariable Long id) {
        return vacationService.denyVacation(id);
    }

    // Admin: Urlaubsantrag löschen
    @DeleteMapping("/{id}")
    public VacationRequest deleteVacation(@PathVariable Long id,
                                          @RequestParam String adminUsername,
                                          @RequestParam String adminPassword) {
        return vacationService.deleteVacation(id, adminUsername, adminPassword);
    }

    // Abfrage des verbleibenden Urlaubsanspruchs
    @GetMapping("/remaining")
    public double getRemainingVacation(@RequestParam String username, @RequestParam int year) {
        return vacationService.calculateRemainingVacationDays(username, year);
    }
}
