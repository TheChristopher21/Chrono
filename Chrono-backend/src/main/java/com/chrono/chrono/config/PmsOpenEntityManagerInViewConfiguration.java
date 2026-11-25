package com.chrono.chrono.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Keeps ordinary view rendering compatible without holding a JDBC connection for an SSE lifetime. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.jpa", name = "open-in-view", havingValue = "true", matchIfMissing = true)
public class PmsOpenEntityManagerInViewConfiguration {

    @Bean
    OpenEntityManagerInViewInterceptor pmsAwareOpenEntityManagerInViewInterceptor(EntityManagerFactory factory) {
        // Providing this bean makes Boot's unconditionally mapped OSIV configuration back off.
        OpenEntityManagerInViewInterceptor interceptor = new OpenEntityManagerInViewInterceptor();
        interceptor.setEntityManagerFactory(factory);
        return interceptor;
    }

    @Bean
    WebMvcConfigurer pmsAwareOpenEntityManagerInViewConfigurer(
            OpenEntityManagerInViewInterceptor pmsAwareOpenEntityManagerInViewInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addWebRequestInterceptor(pmsAwareOpenEntityManagerInViewInterceptor)
                        .excludePathPatterns("/api/pms/properties/{propertyId}/live");
            }
        };
    }
}
