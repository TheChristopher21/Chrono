package com.chrono.chrono.services;

import com.chrono.chrono.dto.TimeTrackingRequest;
import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TimeTrackingService {

    private final TimeTrackingRepository timeTrackingRepository;

    public TimeTrackingService(TimeTrackingRepository timeTrackingRepository) {
        this.timeTrackingRepository = timeTrackingRepository;
    }

    public TimeTracking punchIn(TimeTrackingRequest request) {
        TimeTracking timeTracking = new TimeTracking();
        timeTracking.setUser(request.getUser());
        timeTracking.setPunchIn(request.getPunchIn());
        return timeTrackingRepository.save(timeTracking);
    }

    public TimeTracking punchOut(TimeTrackingRequest request) {
        TimeTracking timeTracking = timeTrackingRepository.findById(request.getId())
                .orElseThrow(() -> new RuntimeException("TimeTracking entry not found"));
        timeTracking.setPunchOut(request.getPunchOut());
        return timeTrackingRepository.save(timeTracking);
    }

    public List<TimeTracking> getUserTimeTracking(Long userId) {
        return timeTrackingRepository.findByUserId(userId);
    }
}
