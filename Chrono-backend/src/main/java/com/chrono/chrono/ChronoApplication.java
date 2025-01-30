package com.chrono.chrono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.chrono.chrono")
public class ChronoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChronoApplication.class, args);
    }
}
