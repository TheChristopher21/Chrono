package com.chrono.chrono.config;

import com.chrono.chrono.services.CustomUserDetailsService;
import com.chrono.chrono.utils.JwtAuthenticationFilter;
import com.chrono.chrono.utils.PasswordEncoderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private PasswordEncoderConfig passwordEncoderConfig;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.allowed-origins:https://chrono-logisch.ch,https://www.chrono-logisch.ch}")
            String configuredOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseOrigins(configuredOrigins));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "Origin", "X-Agent-Token", "X-NFC-Agent-Request"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    static List<String> parseOrigins(String configuredOrigins) {
        List<String> origins = Stream.of(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .distinct()
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS origins must be an explicit non-empty list");
        }
        return origins;
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
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    auth.requestMatchers("/api/auth/**").permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/nfc/command").permitAll();
                    auth.requestMatchers(HttpMethod.PUT, "/api/nfc/command/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/timetracking/punch").permitAll();
                    auth.requestMatchers("/api/nfc/read/1").permitAll();
                    auth.requestMatchers("/api/nfc/write-sector0").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/apply").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/contact").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/public/analytics/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/public/pms/guest-registration/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/public/pms/webhooks/channels/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/holidays/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/public/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/report/timesheet/ics-feed/**").permitAll();

                    auth.requestMatchers("/api/admin/users", "/api/admin/users/**")
                            .hasAnyRole("ADMIN", "SUPERADMIN", "PAYROLL_ADMIN");
                    auth.requestMatchers("/api/admin/company/logo")
                            .hasAnyRole("ADMIN", "SUPERADMIN", "PAYROLL_ADMIN");
                    auth.requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPERADMIN");
                    auth.requestMatchers("/api/supply-chain/**").authenticated();
                    auth.requestMatchers("/api/superadmin/**").hasRole("SUPERADMIN");

                    auth.anyRequest().authenticated();
                })
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
