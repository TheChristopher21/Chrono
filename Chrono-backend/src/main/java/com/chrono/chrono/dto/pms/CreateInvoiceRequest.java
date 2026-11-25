package com.chrono.chrono.dto.pms;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInvoiceRequest(
        @NotNull Long folioId,
        @NotNull LocalDate dueDate,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal vatRate,
        @NotBlank @Size(max = 180) String recipientName,
        @Size(max = 180) String recipientAddress,
        @Size(max = 20) String recipientPostalCode,
        @Size(max = 120) String recipientCity,
        @Size(min = 2, max = 2) String recipientCountryCode,
        @Size(max = 34) String creditorIban,
        @Size(max = 27) String qrReference,
        Boolean useProfileBilling,
        @jakarta.validation.Valid BillingProfile billingProfile,
        @jakarta.validation.constraints.Pattern(regexp="(?i)DE|EN|FR|IT|ES") String languageCode
) {
    public CreateInvoiceRequest(Long folioId, LocalDate dueDate, BigDecimal vatRate, String recipientName,
        String recipientAddress, String recipientPostalCode, String recipientCity, String recipientCountryCode,
        String creditorIban, String qrReference,Boolean useProfileBilling,BillingProfile billingProfile) {
        this(folioId,dueDate,vatRate,recipientName,recipientAddress,recipientPostalCode,recipientCity,recipientCountryCode,
            creditorIban,qrReference,useProfileBilling,billingProfile,null);
    }
    public CreateInvoiceRequest(Long folioId, LocalDate dueDate, BigDecimal vatRate, String recipientName,
        String recipientAddress, String recipientPostalCode, String recipientCity, String recipientCountryCode,
        String creditorIban, String qrReference) {
        this(folioId,dueDate,vatRate,recipientName,recipientAddress,recipientPostalCode,recipientCity,recipientCountryCode,
            creditorIban,qrReference,false,null);
    }
}
