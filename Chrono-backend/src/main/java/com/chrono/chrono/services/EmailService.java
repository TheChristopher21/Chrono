package com.chrono.chrono.services;

import com.chrono.chrono.dto.ApplicationData;
import com.chrono.chrono.dto.ContactMessage;
import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendRegistrationMail(ApplicationData data) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("siefertchristopher@chrono-logisch.ch");
        message.setTo("siefertchristopher@chrono-logisch.ch");
        message.setSubject("Neue Firmenbewerbung: " + data.getCompanyName());

        StringBuilder mailText = new StringBuilder();
        mailText.append("Es hat eine neue Firmenbewerbung stattgefunden:\n\n")
                .append("Firma: ").append(data.getCompanyName()).append("\n")
                .append("Ansprechpartner: ").append(data.getContactName()).append("\n")
                .append("E-Mail: ").append(data.getEmail()).append("\n")
                .append("Telefon: ").append(data.getPhone()).append("\n");

        // Baukastenmodell: Features ausgeben
        if (data.getSelectedFeatureNames() != null && !data.getSelectedFeatureNames().isEmpty()) {
            mailText.append("Gewählte Module: ").append(String.join(", ", data.getSelectedFeatureNames())).append("\n");
        } else if (data.getFeatureSummary() != null) {
            mailText.append("Gewählte Module: ").append(data.getFeatureSummary()).append("\n");
        } else if (data.getSelectedFeatures() != null) {
            mailText.append("Gewählte Module (Keys): ").append(String.join(", ", data.getSelectedFeatures())).append("\n");
        }
        if (data.getCompanyId() != null) {
            mailText.append("Freigeschaltet für Company-ID: ").append(data.getCompanyId()).append("\n");
        }

        if (data.getEmployeeCount() != null) {
            mailText.append("Mitarbeiteranzahl: ").append(data.getEmployeeCount()).append("\n");
        }
        if (data.getBillingPeriod() != null) {
            mailText.append("Abrechnung: ").append(data.getBillingPeriod()).append("\n");
        }
        if (data.getCalculatedPrice() != null) {
            mailText.append("Geschätzter Preis: ").append(data.getCalculatedPrice()).append(" CHF\n");
        }
        if (Boolean.TRUE.equals(data.getIncludeOptionalTraining())) {
            mailText.append("Optionales Intensiv-Onboarding: ").append(data.getOptionalTrainingCost()).append(" CHF\n");
        }
        mailText.append("\nWeitere Infos:\n").append(data.getAdditionalInfo());

        message.setText(mailText.toString());
        sendSafely(message, "registration");
    }

    public void sendContactMail(ContactMessage contact) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("siefertchristopher@chrono-logisch.ch");
        msg.setTo("siefertchristopher@chrono-logisch.ch");
        msg.setSubject("Kontaktanfrage von " + contact.getName());
        String text = "Name: " + contact.getName() + "\n" +
                "E-Mail: " + contact.getEmail() + "\n\n" +
                contact.getMessage();
        msg.setText(text);
        sendSafely(msg, "contact");
    }

    public void sendPayslipGeneratedMail(User user, Payslip payslip) {
        if (user.getEmail() == null || !user.isEmailNotifications()) return;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("siefertchristopher@chrono-logisch.ch");
        msg.setTo(user.getEmail());
        msg.setSubject("Neue Gehaltsabrechnung bereit");
        msg.setText("Ihre Abrechnung vom " + payslip.getPeriodStart() + " bis " + payslip.getPeriodEnd() + " ist erstellt.");
        sendSafely(msg, "payslip-generated");
    }

    public void sendPayslipApprovedMail(User user, Payslip payslip) {
        if (user.getEmail() == null || !user.isEmailNotifications()) return;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("siefertchristopher@chrono-logisch.ch");
        msg.setTo(user.getEmail());
        msg.setSubject("Gehaltsabrechnung freigegeben");
        msg.setText("Ihre Abrechnung vom " + payslip.getPeriodStart() + " bis " + payslip.getPeriodEnd() + " wurde freigegeben.");
        sendSafely(msg, "payslip-approved");
    }

    private void sendSafely(SimpleMailMessage message, String context) {
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            logger.error("Failed to send {} email.", context, ex);
        }
    }
}
