// src/main/java/com/chrono/chrono/services/TimeTrackingService.java
package com.chrono.chrono.services;

import com.chrono.chrono.dto.AdminTimeTrackDTO;
import com.chrono.chrono.dto.TimeTrackingResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TimeTrackingService {

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;
    @Autowired
    private UserRepository userRepository;

    public TimeTrackingResponse punch(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        // Ermittle die Anzahl der heutigen Einträge
        List<TimeTracking> todaysEntries = timeTrackingRepository.findByUserOrderByStartTimeDesc(user)
                .stream()
                .filter(tt -> tt.getStartTime().toLocalDate().equals(LocalDate.now()))
                .collect(Collectors.toList());
        int count = todaysEntries.size();
        if (count >= 4) {
            throw new RuntimeException("All punches for today have been recorded.");
        }

        TimeTracking tt = new TimeTracking();
        LocalDateTime now = LocalDateTime.now();
        tt.setStartTime(now);
        tt.setPunchOrder(count + 1);
        tt.setCorrected(false);
        tt.setUser(user);
        TimeTracking saved = timeTrackingRepository.save(tt);
        return convertToResponse(saved);
    }

    public TimeTrackingResponse punchIn(String username) {
        return punch(username);
    }

    public TimeTrackingResponse punchOut(String username) {
        return punch(username);
    }

    public List<TimeTrackingResponse> getUserHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByStartTimeDesc(user);
        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    public List<AdminTimeTrackDTO> getAllTimeTracksWithUser() {
        List<TimeTracking> all = timeTrackingRepository.findAll();
        return all.stream()
                .map(tt -> new AdminTimeTrackDTO(
                        tt.getUser().getUsername(),
                        tt.getStartTime(),
                        tt.getEndTime(),
                        tt.isCorrected(),
                        tt.getPunchOrder()
                ))
                .collect(Collectors.toList());
    }

    private TimeTrackingResponse convertToResponse(TimeTracking tt) {
        String color;
        switch (tt.getPunchOrder()) {
            case 1: color = "Work Start"; break;
            case 2: color = "Break Start"; break;
            case 3: color = "Break End"; break;
            case 4: color = "Work End"; break;
            default: color = "Unknown";
        }
        return new TimeTrackingResponse(
                tt.getId(),
                tt.getStartTime(),
                tt.getEndTime(),
                tt.isCorrected(),
                color,
                tt.getPunchOrder()
        );
    }
}
