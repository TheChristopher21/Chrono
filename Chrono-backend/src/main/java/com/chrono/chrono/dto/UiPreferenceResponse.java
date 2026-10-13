package com.chrono.chrono.dto;

import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record UiPreferenceResponse(
        UserUiPreferenceArea area,
        String context,
        int schemaVersion,
        long revision,
        JsonNode payload,
        LocalDateTime updatedAt
) {
}
