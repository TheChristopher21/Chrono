package com.chrono.chrono.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendRegistrationNotification(
            String companyName,
            String contactName,
            String email,
            String phone,
            String additionalInfo,
            String chosenPackage // Neu
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("siefertchristopher@chrono-logisch.ch");
        message.setTo("siefertchristopher@chrono-logisch.ch");
        message.setSubject("Neue Firmenbewerbung: " + companyName);

        // Den E-Mail-Text erweitern:
        message.setText(
                "Es hat eine neue Firmenbewerbung stattgefunden:\n\n" +
                        "Firma: " + companyName + "\n" +
                        "Ansprechpartner: " + contactName + "\n" +
                        "E-Mail: " + email + "\n" +
                        "Telefon: " + phone + "\n" +
                        "Gewähltes Paket: " + chosenPackage + "\n" +    // <<-------------
                        "Weitere Infos: " + additionalInfo
        );

        mailSender.send(message);
    }
}
