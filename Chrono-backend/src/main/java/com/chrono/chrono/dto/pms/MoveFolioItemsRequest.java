package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MoveFolioItemsRequest(
        @NotNull Long targetFolioId,
        @NotEmpty @Size(max = 250) List<@NotNull Long> itemIds
) {
}
