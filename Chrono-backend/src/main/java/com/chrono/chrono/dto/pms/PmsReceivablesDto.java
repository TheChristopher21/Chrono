package com.chrono.chrono.dto.pms;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PmsReceivablesDto {
    private PmsReceivablesDto() { }
    public record CreditSettings(boolean enabled,@NotNull @DecimalMin("0.00") BigDecimal creditLimit,@Min(0) @Max(365) int paymentTermsDays) { }
    public record DirectBill(@NotNull Long organizationId) { }
    public record Settlement(@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,@NotBlank @Size(max=180) String bankReference,@NotBlank @Size(max=64) String requestId) { }
    public record Reminder(@NotBlank @Size(max=500) String note) { }
    public record CreditView(Long organizationId,String organizationName,boolean enabled,BigDecimal creditLimit,int paymentTermsDays,BigDecimal outstanding,String currencyCode) { }
    public record Page(List<ReceivableView> items,int page,int size,long totalElements,boolean hasNext,BigDecimal totalOutstanding,BigDecimal totalOverdue) { }
    public record SettlementView(Long id,BigDecimal amount,LocalDate postingDate,String bankReference,String createdBy) { }
    public record ReceivableView(Long id,Long invoiceId,String invoiceNumber,Long folioId,Long groupId,Long organizationId,String organizationName,String currencyCode,LocalDate dueDate,BigDecimal amount,BigDecimal settledAmount,BigDecimal creditedAmount,BigDecimal balance,String status,long daysOverdue,int reminderLevel,LocalDateTime lastReminderAt,List<SettlementView> settlements) { }
}
