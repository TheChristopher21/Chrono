package com.chrono.chrono.controller;

import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.services.VacationService;
import com.chrono.chrono.services.UserService; // NEU import, um userService zu nutzen
import com.chrono.chrono.entities.User;
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

    @Autowired
    private UserService userService; // NEU

    @PostMapping("/create")
    public VacationRequest createVacation(@RequestParam String username,
                                          @RequestParam String startDate,
                                          @RequestParam String endDate,
                                          @RequestParam(required = false, defaultValue = "false") boolean halfDay,
                                          @RequestParam(required = false, defaultValue = "false") boolean usesOvertime) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return vacationService.createVacationRequest(username, start, end, halfDay, usesOvertime);
    }

    @PostMapping("/adminCreate")
    public VacationRequest adminCreateVacation(@RequestParam String adminUsername,
                                               @RequestParam String adminPassword,
                                               @RequestParam String username,
                                               @RequestParam String startDate,
                                               @RequestParam String endDate,
                                               @RequestParam(required = false, defaultValue = "false") boolean halfDay,
                                               @RequestParam(required = false, defaultValue = "false") boolean usesOvertime) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return vacationService.adminCreateVacation(adminUsername, adminPassword, username, start, end, halfDay, usesOvertime);
    }

    @GetMapping("/my")
    public List<VacationRequest> getMyVacations(Principal principal) {
        // principal.getName() => logged in user
        // Hole user + company => filter in Service
        return vacationService.getUserVacations(principal.getName());
    }

    @GetMapping("/all")
    public List<VacationRequest> getAllVacations(Principal principal) {
        User adminUser = userService.getUserByUsername(principal.getName());
        return vacationService.getAllVacationsInCompany(adminUser.getCompany().getId());
    }

    @PostMapping("/approve/{id}")
    public VacationRequest approve(@PathVariable Long id, Principal principal) {
        // vacationService prüft, ob der Request in adminUser.getCompany() liegt
        return vacationService.approveVacation(id, principal.getName());
    }

    @PostMapping("/deny/{id}")
    public VacationRequest deny(@PathVariable Long id, Principal principal) {
        return vacationService.denyVacation(id, principal.getName());
    }

    @DeleteMapping("/{id}")
    public VacationRequest deleteVacation(@PathVariable Long id,
                                          @RequestParam String adminUsername,
                                          @RequestParam String adminPassword) {
        return vacationService.deleteVacation(id, adminUsername, adminPassword);
    }

    @GetMapping("/remaining")
    public double getRemainingVacation(@RequestParam String username, @RequestParam int year) {
        return vacationService.calculateRemainingVacationDays(username, year);
    }
}
