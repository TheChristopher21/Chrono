package com.chrono.chrono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class ChronoApplication {
    public static void main(String[] args) {
        // Setzt die Standardzeitzone explizit auf "Europe/Zurich" (anpassen nach Bedarf)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Zurich"));
        SpringApplication.run(ChronoApplication.class, args);
    }
}
