package com.chrono.chrono.dto;

import java.time.LocalDateTime;

public class TimeTrackingResponse {
    private LocalDateTime punchIn;
    private LocalDateTime punchOut;
    private String status;

    // Konstruktoren
    public TimeTrackingResponse() {}

    public TimeTrackingResponse(LocalDateTime punchIn, LocalDateTime punchOut, String status) {
        this.punchIn = punchIn;
        this.punchOut = punchOut;
        this.status = status;
    }

    // Getter und Setter
    public LocalDateTime getPunchIn() {
        return punchIn;
    }

    public void setPunchIn(LocalDateTime punchIn) {
        this.punchIn = punchIn;
    }

    public LocalDateTime getPunchOut() {
        return punchOut;
    }

    public void setPunchOut(LocalDateTime punchOut) {
        this.punchOut = punchOut;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
