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
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;

import static com.chrono.chrono.config.CorsConfig.ALLOWED_ORIGINS;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private PasswordEncoderConfig passwordEncoderConfig;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Nutze allowedOriginPatterns für flexible Übereinstimmung
        configuration.setAllowedOriginPatterns(Arrays.stream(ALLOWED_ORIGINS).toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Origin"));
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

// Pfad: src/main/java/com/chrono/chrono/config/SecurityConfig.java

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // Öffentliche Endpunkte für Authentifizierung (Login, Registrierung)
                    auth.requestMatchers("/api/auth/**").permitAll();
                    // GET-Anfragen an den NFC-Kommando-Endpunkt erlauben
                    auth.requestMatchers(HttpMethod.GET, "/api/nfc/command").permitAll();
                    // PUT-Anfragen für den NFC-Kommando-Endpunkt erlauben
                    auth.requestMatchers(HttpMethod.PUT, "/api/nfc/command/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/timetracking/punch").permitAll();
                    auth.requestMatchers("/api/nfc/read/1").permitAll();
                    auth.requestMatchers("/api/holidays/**").authenticated(); // Oder permitAll(), falls Feiertage öffentlich sein sollen
                    auth.requestMatchers("/api/sick-leave/**").authenticated();

                    // NEU: Zugriff auf Changelog für alle eingeloggten User erlauben
                    auth.requestMatchers("/api/changelog/**").authenticated();

                    // Der Endpoint zum Schreiben von Sektor 0 bleibt öffentlich
                    auth.requestMatchers("/api/nfc/write-sector0").permitAll();
                    // Den Mail-Endpunkt öffentlich freigeben
                    auth.requestMatchers(HttpMethod.POST, "/api/apply").permitAll();
                    // Schütze Admin-Endpunkte – nur Nutzer mit ROLE_ADMIN dürfen zugreifen
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