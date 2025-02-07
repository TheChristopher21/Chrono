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
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class TimeTrackingService {

    @Autowired
    private TimeTrackingRepository timeTrackingRepository;

    @Autowired
    private UserRepository userRepository;


    public TimeTrackingResponse handlePunch(String username, String newStatus) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found for time tracking"));

        // Letzten Eintrag suchen, der noch nicht beendet (endTime == null)
        Optional<TimeTracking> activePunchOpt = timeTrackingRepository
                .findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(user);
        TimeTracking activePunch = activePunchOpt.orElse(null);

        // Außerdem den allerletzten Eintrag (auch wenn endTime != null) um den punchOrder zu kennen.
        TimeTracking lastFinished = timeTrackingRepository.findTopByUserOrderByIdDesc(user).orElse(null);
        Integer lastPunchOrder = (activePunch != null)
                ? activePunch.getPunchOrder()
                : (lastFinished != null ? lastFinished.getPunchOrder() : null);

        // Prüfen, ob der Übergang erlaubt ist
        if (!isTransitionAllowed(lastPunchOrder, newStatus)) {
            throw new RuntimeException("Transition not allowed from punchOrder=" + lastPunchOrder + " to " + newStatus);
        }

        // Den aktiven Eintrag schließen (endTime setzen), wenn vorhanden
        if (activePunch != null) {
            activePunch.setEndTime(LocalDateTime.now());
            timeTrackingRepository.save(activePunch);
        }

        // Neuen Eintrag anlegen (punchOrder je nach Status)
        int nextOrder = mapStatusToPunchOrder(newStatus);
        TimeTracking newEntry = createNewPunch(user, nextOrder);

        return convertToResponse(newEntry);
    }


    private boolean isTransitionAllowed(Integer lastPunchOrder, String newStatus) {
        switch (newStatus) {
            case "WORK_START":
                // Erlaubt, wenn noch gar nichts da oder der letzte PunchOrder = 4 (Work End)
                return lastPunchOrder == null || lastPunchOrder == 4;
            case "BREAK_START":
                // Erlaubt, wenn letzter PunchOrder = 1 (Work Start)
                return lastPunchOrder != null && lastPunchOrder == 1;
            case "BREAK_END":
                // Erlaubt, wenn letzter PunchOrder = 2 (Break Start)
                return lastPunchOrder != null && lastPunchOrder == 2;
            case "WORK_END":
                // Erlaubt, wenn letzter PunchOrder = 1 (Work Start) oder 3 (Break End)
                return lastPunchOrder != null && (lastPunchOrder == 1 || lastPunchOrder == 3);
            default:
                return false;
        }
    }


    private int mapStatusToPunchOrder(String newStatus) {
        switch (newStatus) {
            case "WORK_START":  return 1;
            case "BREAK_START": return 2;
            case "BREAK_END":   return 3;
            case "WORK_END":    return 4;
            default:            return 0; // "Unknown"
        }
    }


    private TimeTracking createNewPunch(User user, int punchOrder) {
        TimeTracking tt = new TimeTracking();
        tt.setUser(user);
        tt.setPunchOrder(punchOrder);
        tt.setStartTime(LocalDateTime.now());
        tt.setCorrected(false);
        TimeTracking saved = timeTrackingRepository.save(tt);
        return saved;
    }


    public List<TimeTrackingResponse> getUserHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        List<TimeTracking> list = timeTrackingRepository.findByUserOrderByStartTimeDesc(user);
        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
    }


    public List<AdminTimeTrackDTO> getAllTimeTracksWithUser() {
        List<TimeTracking> all = timeTrackingRepository.findAllWithUser();
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


    public AdminTimeTrackDTO updateTimeTrackEntry(Long id,
                                                  LocalDateTime newStart,
                                                  LocalDateTime newEnd,
                                                  String adminPassword,
                                                  String userPassword,
                                                  String adminUsername) {
        TimeTracking tt = timeTrackingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Time tracking entry not found"));

        // Passwörter prüfen, falls nötig, ausgelassen:
        tt.setStartTime(newStart);
        tt.setEndTime(newEnd);
        TimeTracking updated = timeTrackingRepository.save(tt);

        return new AdminTimeTrackDTO(
                updated.getUser().getUsername(),
                updated.getStartTime(),
                updated.getEndTime(),
                updated.isCorrected(),
                updated.getPunchOrder()
        );
    }


    private TimeTrackingResponse convertToResponse(TimeTracking tt) {
        // punchOrder -> Text
        String color;
        switch (tt.getPunchOrder()) {
            case 1:  color = "Work Start";  break;
            case 2:  color = "Break Start"; break;
            case 3:  color = "Break End";   break;
            case 4:  color = "Work End";    break;
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
