package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveMaintenanceWorkOrderRequest(
        @NotBlank @Size(max = 2000) String resolutionNotes
) {
}
