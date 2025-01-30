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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TimeTrackingService {

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;
    @Autowired
    private UserRepository userRepository;

    public TimeTrackingResponse punchIn(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        TimeTracking tt = new TimeTracking();
        tt.setStartTime(LocalDateTime.now());
        tt.setEndTime(null);
        tt.setUser(user);
        tt.setCorrected(false);

        TimeTracking saved = timeTrackingRepository.save(tt);
        return convertToResponse(saved);
    }

    public TimeTrackingResponse punchOut(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        // Letzter offener Eintrag
        TimeTracking open = timeTrackingRepository
                .findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(user)
                .orElseThrow(() -> new RuntimeException("No open punch-in found"));

        open.setEndTime(LocalDateTime.now());
        open.setCorrected(false);
        TimeTracking saved = timeTrackingRepository.save(open);

        return convertToResponse(saved);
    }

    // Eigene History
    public List<TimeTrackingResponse> getUserHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByStartTimeDesc(user);
        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    // NEU: Für Manager => TimeTracks aller User im Team
    public List<TimeTrackingResponse> getTeamTimeTracks(User manager) {
        // Finde alle Users, die manager = manager
        List<User> teamUsers = userRepository.findByManager(manager);

        // Sammle alle TimeTrackings von diesen Team-Usern
        List<TimeTracking> allTracks = teamUsers.stream()
                .flatMap(u -> timeTrackingRepository.findByUserOrderByStartTimeDesc(u).stream())
                .collect(Collectors.toList());

        return allTracks.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<AdminTimeTrackDTO> getAllTimeTracksWithUser() {
        List<TimeTracking> all = timeTrackingRepository.findAll();
        return all.stream()
                .map(tt -> new AdminTimeTrackDTO(
                        tt.getUser().getUsername(),
                        tt.getStartTime(),
                        tt.getEndTime(),
                        tt.isCorrected()
                ))
                .collect(Collectors.toList());
    }

    private TimeTrackingResponse convertToResponse(TimeTracking tt) {
        String color;
        if (tt.getEndTime() == null) {
            color = "RED"; // noch nicht ausgestempelt
        } else if (tt.isCorrected()) {
            color = "ORANGE";
        } else {
            color = "GREEN";
        }

        return new TimeTrackingResponse(
                tt.getId(),
                tt.getStartTime(),
                tt.getEndTime(),
                tt.isCorrected(),
                color
        );
    }
}
