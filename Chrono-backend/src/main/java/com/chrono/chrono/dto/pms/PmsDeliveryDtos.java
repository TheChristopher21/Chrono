package com.chrono.chrono.dto.pms;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class PmsDeliveryDtos {
    private PmsDeliveryDtos() {}
    public record Settings(long version,boolean mailEnabled,@Email @Size(max=190) String senderEmail,
        @Size(max=120) String senderName,@Email @Size(max=190) String replyTo,boolean automaticInvoices,
        @NotNull @Pattern(regexp="DE|EN|FR|IT|ES") String invoiceLanguage,boolean automaticReminders,boolean sendReminders,
        @Min(1) @Max(365) int firstReminderDays,@Min(1) @Max(365) int reminderIntervalDays,@Min(1) @Max(12) int maxReminders,
        @NotBlank @Size(max=240) String invoiceSubject,@NotBlank @Size(max=8000) String invoiceBody,
        @NotBlank @Size(max=240) String reminderSubject,@NotBlank @Size(max=8000) String reminderBody) {}
    public record InvoiceSend(@NotBlank @Size(max=120) String requestId,@Email @Size(max=190) String recipient) {}
    public record Retry(boolean acknowledgePossibleDuplicate) {}
    public record AttachmentView(Long id,String filename,String contentType,int bytes,String sha256) {}
    public record DeliveryView(Long id,Long invoiceId,String requestId,String kind,String recipient,String senderEmail,
        String subject,String status,int attempts,LocalDateTime nextAttemptAt,LocalDateTime createdAt,LocalDateTime sentAt,
        String lastError,String messageId,List<AttachmentView> attachments) {}
    public record DeliveryPage(List<DeliveryView> items,int page,int size,long totalElements,boolean hasNext) {}
    public record Attachment(String filename,String contentType,byte[] content) {}
}
