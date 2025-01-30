package com.chrono.chrono.controller;

import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.services.UserService;
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
    @Autowired
    private UserService userService;

    // 1) User beantragt Urlaub
    @PostMapping("/create")
    public VacationRequest createVacation(@RequestParam String username,
                                          @RequestParam String startDate,
                                          @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return vacationService.createVacationRequest(username, start, end);
    }

    // 2) User kann eigene Urlaubsanträge sehen
    @GetMapping("/my")
    public List<VacationRequest> getMyVacations(Principal principal) {
        // principal.getName() => username
        return vacationService.getUserVacations(principal.getName());
    }

    // 3) Admin/Manager: Approve
    @PostMapping("/approve/{id}")
    public VacationRequest approve(@PathVariable Long id) {
        return vacationService.approveVacation(id);
    }

    @PostMapping("/deny/{id}")
    public VacationRequest deny(@PathVariable Long id) {
        return vacationService.denyVacation(id);
    }
}
