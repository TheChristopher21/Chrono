package com.chrono.chrono.services;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.repositories.TimeTrackingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TimeTrackingService {

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    public TimeTracking getLatestTimeTracking(Long userId) {
        return timeTrackingRepository.findFirstByUserIdOrderByCheckInTimeDesc(userId)
                .orElse(null);
    }

    public List<TimeTracking> getWeekStats(Long userId) {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
        return timeTrackingRepository.findAllByUserIdAndCheckInTimeAfter(userId, oneWeekAgo);
    }

    public String checkIn(Long userId) {
        TimeTracking entry = new TimeTracking();
        entry.setUserId(userId); // Hier setzen wir die userId direkt
        entry.setCheckInTime(LocalDateTime.now());
        timeTrackingRepository.save(entry);
        return "Eingestempelt!";
    }

    public String checkOut(Long userId) {
        Optional<TimeTracking> lastEntry = timeTrackingRepository.findFirstByUserIdOrderByCheckInTimeDesc(userId);
        if (lastEntry.isPresent() && lastEntry.get().getCheckOutTime() == null) {
            TimeTracking entry = lastEntry.get();
            entry.setCheckOutTime(LocalDateTime.now());
            timeTrackingRepository.save(entry);
            return "Ausgestempelt!";
        }
        return "Kein aktiver Check-In gefunden.";
    }
}
