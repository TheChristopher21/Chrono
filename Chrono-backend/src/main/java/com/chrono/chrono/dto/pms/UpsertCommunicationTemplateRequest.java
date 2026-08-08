package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertCommunicationTemplateRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 240) String subject,
        @NotBlank @Size(max = 8000) String body,
        @Size(min = 2, max = 8) String languageCode,
        boolean active
) {
}
