package com.chrono.chrono.services;

import com.chrono.chrono.dto.DashboardResponse;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    public DashboardResponse getUserDashboard(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("user not found"));

        // Hier holen wir alle TimeTrackings für den User, absteigend sortiert
        var timeEntries = timeTrackingRepository.findByUserOrderByStartTimeDesc(user)
                .stream()
                .map(tt -> tt.getStartTime() + " - " + (tt.getEndTime() == null ? "..." : tt.getEndTime()))
                .collect(Collectors.toList());

        // Beispielhaft: Rolle (falls du ManyToMany ohne UserRole nutzt)
        String roleName = user.getRoles().isEmpty()
                ? "NONE"
                : user.getRoles().iterator().next().getRoleName();

        return new DashboardResponse(user.getUsername(), timeEntries, roleName);
    }
}
