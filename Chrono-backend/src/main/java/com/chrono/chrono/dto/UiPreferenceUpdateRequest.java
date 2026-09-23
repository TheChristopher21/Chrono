package com.chrono.chrono.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UiPreferenceUpdateRequest(
        @NotNull @Min(1) Integer schemaVersion,
        @NotNull @Min(0) Long revision,
        @NotNull JsonNode payload
) {
}
