package com.chrono.chrono.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    /**
     * Standard RestTemplate for normal, short API calls.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(20))
                .setReadTimeout(Duration.ofMinutes(2)) // 2 minutes timeout for normal chats
                .build();
    }

    /**
     * A special RestTemplate with a very long timeout, only for the LLM warm-up.
     */
    @Bean
    public RestTemplate longTimeoutRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofMinutes(20)) // 20 minutes timeout for model download
                .build();
    }
}