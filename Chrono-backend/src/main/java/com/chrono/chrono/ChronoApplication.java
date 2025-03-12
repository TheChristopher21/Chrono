package com.chrono.chrono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ChronoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChronoApplication.class, args);
    }
}
