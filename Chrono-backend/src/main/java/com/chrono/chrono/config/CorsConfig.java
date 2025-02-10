package com.chrono.chrono.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    /**
     * Definiert globale CORS-Regeln für deine Spring-Anwendung.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Erlaube CORS für alle Pfade (/**),
                // gestattet als Origin nur "http://localhost:5173",
                // alle HTTP-Methoden, alle Header,
                // und Credentials (Cookies/Authorization) sind erlaubt.
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:5173")   // kein "*"
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Authorization")
                        .allowCredentials(true);
            }
        };
    }
}
