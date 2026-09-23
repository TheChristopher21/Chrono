package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CorrectInvoiceRequest(
        @NotBlank @Size(max = 500) String reason,
        @jakarta.validation.constraints.Pattern(regexp = "REISSUE|CANCEL_SERVICES") String mode
) {
    public CorrectInvoiceRequest(String reason) { this(reason, "REISSUE"); }
    public String effectiveMode() { return mode == null ? "REISSUE" : mode; }
}
