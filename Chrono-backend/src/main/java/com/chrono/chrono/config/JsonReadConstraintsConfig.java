package com.chrono.chrono.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JsonReadConstraintsConfig {

    static final int MAX_JSON_NESTING_DEPTH = 64;
    static final int MAX_JSON_STRING_LENGTH = 8 * 1024 * 1024;
    static final int MAX_JSON_NUMBER_LENGTH = 1_000;
    static final long MAX_JSON_DOCUMENT_LENGTH = 10L * 1024 * 1024;
    static final long MAX_JSON_TOKEN_COUNT = 1_000_000L;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonStreamReadConstraintsCustomizer() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(MAX_JSON_NESTING_DEPTH)
                .maxStringLength(MAX_JSON_STRING_LENGTH)
                .maxNumberLength(MAX_JSON_NUMBER_LENGTH)
                .maxDocumentLength(MAX_JSON_DOCUMENT_LENGTH)
                .maxTokenCount(MAX_JSON_TOKEN_COUNT)
                .build();
        return builder -> builder.postConfigurer(
                objectMapper -> objectMapper.getFactory().setStreamReadConstraints(constraints)
        );
    }
}
