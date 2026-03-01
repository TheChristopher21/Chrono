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
    private final String providerReference;
    private final String providerStatus;
    private final String providerMessage;

    public SecureMessageDTO(Long id, String recipient, String subject, String transport,
                            boolean delivered, LocalDateTime sentAt,
                            String providerReference, String providerStatus, String providerMessage) {
        this.id = id;
        this.recipient = recipient;
        this.subject = subject;
        this.transport = transport;
        this.delivered = delivered;
        this.sentAt = sentAt;
        this.providerReference = providerReference;
        this.providerStatus = providerStatus;
        this.providerMessage = providerMessage;
    }

    public static SecureMessageDTO from(SecureMessage message) {
        return new SecureMessageDTO(
                message.getId(),
                message.getRecipient(),
                message.getSubject(),
                message.getTransport(),
                message.isDelivered(),
                message.getSentAt(),
                message.getProviderReference(),
                message.getProviderStatus(),
                message.getProviderMessage());
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

    public String getProviderReference() {
        return providerReference;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getProviderMessage() {
        return providerMessage;
    }
}
