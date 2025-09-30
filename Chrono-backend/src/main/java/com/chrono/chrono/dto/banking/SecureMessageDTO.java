package com.chrono.chrono.dto.banking;

import com.chrono.chrono.entities.banking.SecureMessage;

import java.time.LocalDateTime;

public class SecureMessageDTO {
    private final Long id;
    private final String recipient;
    private final String subject;
    private final String transport;
    private final boolean delivered;
    private final LocalDateTime sentAt;

    public SecureMessageDTO(Long id, String recipient, String subject, String transport,
                            boolean delivered, LocalDateTime sentAt) {
        this.id = id;
        this.recipient = recipient;
        this.subject = subject;
        this.transport = transport;
        this.delivered = delivered;
        this.sentAt = sentAt;
    }

    public static SecureMessageDTO from(SecureMessage message) {
        return new SecureMessageDTO(
                message.getId(),
                message.getRecipient(),
                message.getSubject(),
                message.getTransport(),
                message.isDelivered(),
                message.getSentAt());
    }

    public Long getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getTransport() {
        return transport;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
