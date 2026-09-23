package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Getter @Setter @Table(name="pms_billing_settings")
public class PmsBillingSettings {
    @Id private Long propertyId;
    @MapsId @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="property_id") private HotelProperty property;
    @Version private long version;
    private boolean mailEnabled;
    @Column(length=190) private String senderEmail;
    @Column(length=120) private String senderName;
    @Column(length=190) private String replyTo;
    private boolean automaticInvoices;
    @Column(nullable=false,length=5) private String invoiceLanguage="DE";
    private boolean automaticReminders;
    private boolean sendReminders;
    private int firstReminderDays=7;
    private int reminderIntervalDays=14;
    private int maxReminders=3;
    @Column(length=240) private String invoiceSubject="Invoice {{invoiceNumber}} · {{hotelName}}";
    @Column(length=8000,columnDefinition="text") private String invoiceBody="Please find invoice {{invoiceNumber}} attached. Total: {{amount}} {{currency}}. Due: {{dueDate}}.";
    @Column(length=240) private String reminderSubject="Payment reminder {{invoiceNumber}} · {{hotelName}}";
    @Column(length=8000,columnDefinition="text") private String reminderBody="Our records show an outstanding balance of {{amount}} {{currency}} for invoice {{invoiceNumber}}, due {{dueDate}}. Reminder {{level}}.";
    private LocalDateTime updatedAt;
    @Column(length=120) private String updatedBy;
}
