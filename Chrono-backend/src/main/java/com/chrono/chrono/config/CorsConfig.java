package com.chrono.chrono.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    // Erlaube Anfragen von der Domain, der IP und localhost
    public static final String[] ALLOWED_ORIGINS = {
            "https://chrono-logisch.ch",
            "http://localhost:5173"
    };

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        // Verwende allowedOriginPatterns für flexiblere Übereinstimmung
                        .allowedOriginPatterns(ALLOWED_ORIGINS)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
