package com.chrono.chrono.services.pms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class PublicBookingVerificationMailer {
    private static final Logger log = LoggerFactory.getLogger(PublicBookingVerificationMailer.class);
    private final JavaMailSender mailSender;
    private final String publicBaseUrl;
    private final String sender;

    public PublicBookingVerificationMailer(JavaMailSender mailSender,
            @Value("${app.public-base-url:https://chrono-logisch.ch}") String publicBaseUrl,
            @Value("${app.mail.from:no-reply@chrono-logisch.ch}") String sender) {
        this.mailSender = mailSender;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PublicBookingVerificationRequested event) {
        String link = publicBaseUrl + "/book/" + event.publicSlug() + "?verificationToken="
                + URLEncoder.encode(event.token(), StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(event.recipient());
        message.setSubject("Buchung bei " + event.hotelName() + " bestätigen");
        message.setText("Bitte bestätigen Sie Ihre Buchungsanfrage " + event.confirmationCode()
                + " innerhalb der angegebenen Frist:\n\n" + link
                + "\n\nOhne Bestätigung wird die Zimmerreservierung automatisch freigegeben.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.error("Public booking verification mail failed for confirmation {}",
                    event.confirmationCode(), exception);
        }
    }
}
