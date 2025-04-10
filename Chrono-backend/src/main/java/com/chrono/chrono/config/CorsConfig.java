package com.chrono.chrono.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    // Erlaubte Ursprünge (Domains, IPs, etc.)
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
                        // Nutzt flexible Patterns, sodass auch z. B. Subdomains passen können
                        .allowedOriginPatterns(ALLOWED_ORIGINS)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Authorization") // Damit der Authorization-Header auch vom Client gelesen werden kann
                        .allowCredentials(true)
                        .maxAge(3600); // Cache-Dauer in Sekunden (optional)
            }
        };
    }
}
