package com.chrono.chrono.jobs;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.chrono.chrono.services.TimeTrackingService;

@Component
@RequiredArgsConstructor          // erzeugt einen Konstruktor mit allen final-Feldern
public class BalanceBootstrap {

    private final TimeTrackingService timeTrackingService;

    /** wird einmalig direkt nach dem Hochfahren ausgeführt */
    @PostConstruct
    public void runOnceAfterStartup() {
        timeTrackingService.bookDailyBalances();            // Vollzeit + Stundenlöhner
        timeTrackingService.bookWeeklyPercentageBalances(); // Percentage-User
    }
}
