package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record MergeOrganizationsRequest(
        @NotNull Long targetOrganizationId,
        @Size(max = 20) Set<String> takeFromSource
) {
}
