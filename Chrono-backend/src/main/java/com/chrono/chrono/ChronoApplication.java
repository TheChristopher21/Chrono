package com.chrono.chrono;

// Stellen Sie sicher, dass diese Imports vorhanden sind
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChronoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChronoApplication.class, args);
    }

    // ===================================================================
    // HINZUFÜGEN: Dieser Block ist zur Fehlersuche absolut entscheidend!
    // ===================================================================
    @Bean
    public CommandLineRunner commandLineRunner(DataSource dataSource) {
        return args -> {
            System.out.println();
            System.out.println("!!!!!!!!!!!!!!!!!! WICHTIGER DEBUG-OUTPUT !!!!!!!!!!!!!!!!!!!");
            try {
                // Diese Zeile druckt die URL, die Spring WIRKLICH verwendet
                System.out.println("!!! Tatsaechlich verwendete Datasource URL: " + dataSource.getConnection().getMetaData().getURL());
            } catch (Exception e) {
                System.out.println("!!! Konnte Datasource URL nicht abrufen: " + e.getMessage());
            }
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.out.println();
        };
    }
}