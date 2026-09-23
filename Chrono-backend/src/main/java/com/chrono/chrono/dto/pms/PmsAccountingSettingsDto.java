package com.chrono.chrono.dto.pms;
import jakarta.validation.constraints.*;
import java.util.Map;

public record PmsAccountingSettingsDto(
    @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._/-]{0,31}") String guestReceivableAccount,
    @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._/-]{0,31}") String corporateReceivableAccount,
    @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._/-]{0,31}") String bankAccount,
    @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._/-]{0,31}") String posRevenueAccount,
    @NotNull Map<String,String> revenueAccounts,
    @NotNull Map<String,String> paymentAccounts
) {}
