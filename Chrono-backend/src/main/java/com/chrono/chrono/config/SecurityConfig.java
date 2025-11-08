package com.chrono.chrono.config;

import com.chrono.chrono.services.CustomUserDetailsService;
import com.chrono.chrono.utils.JwtAuthenticationFilter;
import com.chrono.chrono.utils.PasswordEncoderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

// SecurityConfig.java

    // PASST FÜR MOBILE ENTWICKLUNG AN
    public static final String[] ALLOWED_ORIGINS = {
            // Produktive URLs
            "https://chrono-logisch.ch",
            "https://www.chrono-logisch.ch",

            // KORREKTUR: HINZUFÜGEN DES LOKALEN WEB-APP-PORTS
            "http://localhost:5173",

            // Lokale IPs und Ports
            "http://10.0.2.2:5173",
            "http://10.0.2.2:8080",
            "http://localhost:8080",

            // NEU: Die Ursprünge des Capacitor WebViews (siehe Log-Fehler!)
            "capacitor://localhost",   // Standard Capacitor Origin
            "https://localhost"        // Die Origin, die in Ihrem Logcat-Fehler aufgetreten ist
    };
    // ...
    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private PasswordEncoderConfig passwordEncoderConfig;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(ALLOWED_ORIGINS)); // Verwende List.of
        configuration.setAllowedOrigins(List.of(ALLOWED_ORIGINS)); // Erlaubt nur die definierten Origins
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization")); // Wichtig für das Lesen des Tokens im Frontend
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoderConfig.passwordEncoder());
        return authProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults()) // Nutzt die obige corsConfigurationSource Bean
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // WICHTIGSTE ÄNDERUNG: Erlaube alle Preflight-Anfragen
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    // Öffentliche Endpunkte
                    auth.requestMatchers("/api/auth/**").permitAll();
                    auth.requestMatchers("/actuator/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/nfc/command").permitAll();
                    auth.requestMatchers(HttpMethod.PUT, "/api/nfc/command/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/timetracking/punch").permitAll();
                    auth.requestMatchers("/api/nfc/read/1").permitAll();
                    auth.requestMatchers("/api/nfc/write-sector0").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/apply").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/contact").permitAll();
                    auth.requestMatchers("/api/chat").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/public/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/report/timesheet/ics-feed/**").permitAll();

                    // Admin-Endpunkte
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    auth.requestMatchers("/api/superadmin/**").hasRole("SUPERADMIN");

                    // Alle anderen Endpunkte erfordern eine gültige Authentifizierung
                    auth.anyRequest().authenticated();
                })
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}