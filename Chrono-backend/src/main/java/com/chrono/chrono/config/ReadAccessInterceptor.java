package com.chrono.chrono.config;

import com.chrono.chrono.entities.ReadAccessAudit;
import com.chrono.chrono.repositories.ReadAccessAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

@Component
public class ReadAccessInterceptor implements HandlerInterceptor {
    @Autowired
    private ReadAccessAuditRepository repo;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                ReadAccessAudit log = new ReadAccessAudit();
                log.setUsername(auth.getName());
                log.setPath(request.getRequestURI());
                log.setTimestamp(LocalDateTime.now());
                repo.save(log);
            }
        }
        return true;
    }
}
