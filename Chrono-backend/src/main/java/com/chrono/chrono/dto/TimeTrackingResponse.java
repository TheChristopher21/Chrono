package com.chrono.chrono.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Einfache DTO-Klasse für die Rückgabe eines TimeTracking-Eintrags,
 * inkl. "color" (also Art des Punches), "punchOrder" und "dailyNote".
 */
@Setter
@Getter
public class TimeTrackingResponse {

    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean corrected;
    private String color;       // z. B. "Work Start", "Break Start", ...
    private Integer punchOrder; // 1..4
    private String dailyNote;   // Tägliche Notiz

    public TimeTrackingResponse(Long id,
                                LocalDateTime startTime,
                                LocalDateTime endTime,
                                boolean corrected,
                                String color,
                                Integer punchOrder,
                                String dailyNote) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corrected = corrected;
        this.color = color;
        this.punchOrder = punchOrder;
        this.dailyNote = dailyNote;
    }

}
