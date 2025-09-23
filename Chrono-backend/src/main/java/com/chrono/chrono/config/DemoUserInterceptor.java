package com.chrono.chrono.config;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;

@Component
public class DemoUserInterceptor implements HandlerInterceptor {

    @Autowired
    private UserRepository userRepository;

    private static final Set<String> BLOCKED_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> ALLOWED_WRITE_ENDPOINTS = Set.of(
            "/api/vacation/create",
            "/api/correction/create",
            "/api/sick-leave/report",
            "/api/chat"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (BLOCKED_METHODS.contains(request.getMethod())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                User user = userRepository.findByUsername(auth.getName()).orElse(null);
                if (user != null && user.isDemo()) {
                    String path = request.getRequestURI();
                    if (ALLOWED_WRITE_ENDPOINTS.contains(path)) {
                        return true;
                    }
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    return false;
                }
            }
        }
        return true;
    }
}
