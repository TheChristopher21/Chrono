package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.ChannelEnvironment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateChannelConnectionRequest(
        @NotBlank @Size(max = 60) String providerCode,
        @NotBlank @Size(max = 120) String displayName,
        @NotNull ChannelEnvironment environment,
        @Size(max = 180)
        @Pattern(
                regexp = "^env:[A-Z][A-Z0-9_]{2,100}$",
                message = "Die Zugangsdaten-Referenz muss eine Server-Variable im Format env:NAME sein.")
        String secretReference,
        @NotEmpty List<@Valid Mapping> mappings
) {
    public record Mapping(
            @NotNull Long roomTypeId,
            @NotNull Long ratePlanId,
            @NotBlank @Size(max = 100) String externalRoomCode,
            @NotBlank @Size(max = 100) String externalRateCode
    ) {
    }
}
