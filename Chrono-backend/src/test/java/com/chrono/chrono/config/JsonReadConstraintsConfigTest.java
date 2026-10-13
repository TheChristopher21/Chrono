package com.chrono.chrono.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonReadConstraintsConfigTest {

    @Test
    void configuresGlobalDocumentDepthStringNumberAndTokenLimits() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JsonReadConstraintsConfig().jsonStreamReadConstraintsCustomizer().customize(builder);
        ObjectMapper objectMapper = builder.build();

        StreamReadConstraints constraints = objectMapper.getFactory().streamReadConstraints();
        assertEquals(JsonReadConstraintsConfig.MAX_JSON_NESTING_DEPTH, constraints.getMaxNestingDepth());
        assertEquals(JsonReadConstraintsConfig.MAX_JSON_STRING_LENGTH, constraints.getMaxStringLength());
        assertEquals(JsonReadConstraintsConfig.MAX_JSON_NUMBER_LENGTH, constraints.getMaxNumberLength());
        assertEquals(JsonReadConstraintsConfig.MAX_JSON_DOCUMENT_LENGTH, constraints.getMaxDocumentLength());
        assertEquals(JsonReadConstraintsConfig.MAX_JSON_TOKEN_COUNT, constraints.getMaxTokenCount());
    }
}
