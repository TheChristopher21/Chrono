package com.chrono.chrono.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payslip_audit")
public class PayslipAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payslip_id")
    private Payslip payslip;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String author;

    private String comment;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public PayslipAudit() {}

    public PayslipAudit(Payslip payslip, String action, String author, String comment) {
        this.payslip = payslip;
        this.action = action;
        this.author = author;
        this.comment = comment;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Payslip getPayslip() { return payslip; }
    public void setPayslip(Payslip payslip) { this.payslip = payslip; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
