package com.chrono.chrono.dto.pms;

import com.chrono.chrono.entities.pms.FolioItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PostFolioItemRequest(
        @NotNull LocalDate serviceDate,
        @NotNull FolioItemType type,
        @NotBlank @Size(max = 240) String description,
        @NotNull @DecimalMin("0.01") @jakarta.validation.constraints.Digits(integer = 8, fraction = 2) BigDecimal quantity,
        @NotNull BigDecimal unitPrice,
        @DecimalMin("0.00") @jakarta.validation.constraints.DecimalMax("100.00") BigDecimal taxRate
) {
    public PostFolioItemRequest(LocalDate serviceDate, FolioItemType type, String description, BigDecimal quantity, BigDecimal unitPrice) {
        this(serviceDate, type, description, quantity, unitPrice, BigDecimal.ZERO);
    }
}
