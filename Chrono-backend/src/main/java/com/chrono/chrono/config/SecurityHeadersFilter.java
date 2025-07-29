package com.chrono.chrono.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Erzwingt HTTPS mittels HSTS (HTTP Strict Transport Security)
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // Verhindert MIME-Sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Verhindert Clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // Setzt eine Content-Security-Policy (anpassen an deine Anforderungen)
        // KORRIGIERT: Erlaubt Verbindungen (connect-src) zur API-Domain
        response.setHeader("Content-Security-Policy", "default-src 'self'; connect-src 'self' https://api.chrono-logisch.ch");

        // Referrer-Policy, um zu kontrollieren, welche Informationen beim Navigieren weitergegeben werden
        response.setHeader("Referrer-Policy", "no-referrer");

        // Optionale Header: z. B. Cache-Control
        // response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        filterChain.doFilter(request, response);
    }
}