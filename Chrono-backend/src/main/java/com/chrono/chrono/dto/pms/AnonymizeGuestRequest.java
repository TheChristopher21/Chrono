package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnonymizeGuestRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
