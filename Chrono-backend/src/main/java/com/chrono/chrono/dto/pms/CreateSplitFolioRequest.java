package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSplitFolioRequest(
        @NotNull Long reservationId,
        @NotBlank @Size(max = 120) String label,
        Long organizationId
) {
}
