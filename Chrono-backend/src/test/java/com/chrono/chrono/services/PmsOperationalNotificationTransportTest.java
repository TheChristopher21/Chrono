package com.chrono.chrono.services;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PmsOperationalNotificationTransportTest {
    @Test
    void durableEmailSenderPropagatesSmtpFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("Synthetic SMTP failure")).when(sender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> new EmailService(sender).sendOperationalAlertChecked(
                "operations@example.com", "Test alarm", "Synthetic alarm"))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    void durableEmailSenderRequiresAnActualRecipient() {
        JavaMailSender sender = mock(JavaMailSender.class);

        assertThatThrownBy(() -> new EmailService(sender).sendOperationalAlertChecked("", "Test", "Synthetic"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sender);
    }

    @Test
    void durableWebhookSenderRejectsNonSuccessfulAcknowledgement() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("redirect", HttpStatus.TEMPORARY_REDIRECT));

        assertThatThrownBy(() -> new ExternalNotificationService(rest)
                .sendOperationalWebhookChecked("https://example.com/synthetic", "Synthetic alarm"))
                .isInstanceOf(IllegalStateException.class);
    }
}
