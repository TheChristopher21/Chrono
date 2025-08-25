package com.chrono.chrono.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuditConfig implements WebMvcConfigurer {
    @Autowired
    private ReadAccessInterceptor interceptor;

    @Autowired
    private DemoUserInterceptor demoInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
        registry.addInterceptor(demoInterceptor);
    }
}
