package com.chrono.chrono.dto.pms;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
public final class PmsBillingAutomationDtos {
 private PmsBillingAutomationDtos(){}
 public record ReminderCandidate(Long receivableId,String invoiceNumber,String organization,LocalDate dueDate,BigDecimal outstandingAmount,String currencyCode,int nextLevel,LocalDate eligibleOn,boolean eligible,String reason){}
 public record ReminderView(Long id,Long receivableId,String invoiceNumber,int level,LocalDate businessDate,BigDecimal outstandingAmount,String currencyCode,Long deliveryId){}
 public record ReminderRun(int created,List<ReminderView> notices,List<String> skipped){}
 public record BankSuggestion(Long receivableId,String invoiceNumber,String organization,BigDecimal outstandingAmount,String reason){}
 public record BankRow(Long id,String bankAccount,String externalId,LocalDate bookingDate,String currencyCode,BigDecimal amount,String reference,String debtorName,String status,Long receivableId,List<BankSuggestion> suggestions){}
 public record BankImportView(Long id,String filename,String sha256,int rowCount,LocalDateTime createdAt,List<BankRow> rows){}
 public record BankMatch(@NotNull Long transactionId,@NotNull Long receivableId){}
 public record BankConfirm(@NotNull @Size(min=1,max=500) List<@jakarta.validation.Valid BankMatch> matches){}
 public record ExportCreate(@NotBlank @Size(max=120) String requestId,@NotNull LocalDate fromDate,@NotNull LocalDate toExclusive){}
 public record ExportAck(@NotBlank @Size(max=190) String reference){}
 public record ExportView(Long id,String requestId,LocalDate fromDate,LocalDate toExclusive,String status,String sha256,int bytes,LocalDateTime createdAt,LocalDateTime acknowledgedAt,String acknowledgementReference){}
}
