package com.chrono.chrono.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PmsAccessMvcConfiguration implements WebMvcConfigurer {
    private final PmsAccessPolicy policy;
    public PmsAccessMvcConfiguration(PmsAccessPolicy policy) { this.policy = policy; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                policy.authorize(request, null); return true;
            }
        }).addPathPatterns("/api/pms/**");
    }
}
