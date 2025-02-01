// src/main/java/com/chrono/chrono/services/VacationService.java
package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.VacationRequest;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.VacationRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class VacationService {

    @Autowired
    private VacationRequestRepository vacationRepo;
    @Autowired
    private UserRepository userRepo;

    public VacationRequest createVacationRequest(String username, LocalDate start, LocalDate end) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        VacationRequest vr = new VacationRequest();
        vr.setUser(user);
        vr.setStartDate(start);
        vr.setEndDate(end);
        vr.setApproved(false);
        vr.setDenied(false);
        return vacationRepo.save(vr);
    }

    public List<VacationRequest> getUserVacations(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return vacationRepo.findByUser(user);
    }

    public VacationRequest approveVacation(Long vacationId) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));
        vr.setApproved(true);
        vr.setDenied(false);
        return vacationRepo.save(vr);
    }

    public VacationRequest denyVacation(Long vacationId) {
        VacationRequest vr = vacationRepo.findById(vacationId)
                .orElseThrow(() -> new RuntimeException("Vacation request not found"));
        vr.setApproved(false);
        vr.setDenied(true);
        return vacationRepo.save(vr);
    }

    public List<VacationRequest> getAllVacations() {
        return vacationRepo.findAll();
    }
}
