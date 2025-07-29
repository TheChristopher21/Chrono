package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UserScheduleDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/user/schedule")
public class UserScheduleController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<UserScheduleDTO> getSchedule(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserScheduleDTO dto = new UserScheduleDTO(
                user.getScheduleCycle(),
                user.getWeeklySchedule(),
                user.getScheduleEffectiveDate()
        );

        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> updateSchedule(@RequestBody UserScheduleDTO dto, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (dto.getScheduleCycle() != null) {
            user.setScheduleCycle(dto.getScheduleCycle());
        }
        if (dto.getWeeklySchedule() != null) {
            user.setWeeklySchedule(dto.getWeeklySchedule());
        }
        if (dto.getScheduleEffectiveDate() != null) {
            user.setScheduleEffectiveDate(dto.getScheduleEffectiveDate());
        }
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}
