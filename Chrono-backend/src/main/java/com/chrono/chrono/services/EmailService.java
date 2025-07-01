package com.chrono.chrono.services;

import com.chrono.chrono.dto.ApplicationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendRegistrationMail(ApplicationData data) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("siefertchristopher@chrono-logisch.ch");
        message.setTo("siefertchristopher@chrono-logisch.ch");
        message.setSubject("Neue Firmenbewerbung: " + data.getCompanyName());

        String mailText = "Es hat eine neue Firmenbewerbung stattgefunden:\n\n"
                + "Firma: " + data.getCompanyName() + "\n"
                + "Ansprechpartner: " + data.getContactName() + "\n"
                + "E-Mail: " + data.getEmail() + "\n"
                + "Telefon: " + data.getPhone() + "\n"
                + "Gewähltes Paket: " + data.getChosenPackage() + "\n";

        // Nur anzeigen, falls nicht null / Enterprise
        if (data.getEmployeeCount() != null && data.getChosenPackage() != null && !data.getChosenPackage().equals("Enterprise")) {
            mailText += "Mitarbeiteranzahl: " + data.getEmployeeCount() + "\n";
        }
        if (data.getBillingPeriod() != null) {
            mailText += "Abrechnung: " + data.getBillingPeriod() + "\n"; // monthly/yearly
        }
        if (data.getCalculatedPrice() != null) {
            mailText += "Geschätzter Preis: " + data.getCalculatedPrice() + " €\n";
        }

        if (Boolean.TRUE.equals(data.getIncludeOptionalTraining())) {
            mailText += "Optionales Intensiv-Onboarding: " + data.getOptionalTrainingCost() + " CHF\n";
        }

        mailText += "\nWeitere Infos:\n" + data.getAdditionalInfo();

        message.setText(mailText);

        mailSender.send(message);
    }
}
