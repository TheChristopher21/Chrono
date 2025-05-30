package com.chrono.chrono.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "sick_leaves")
public class SickLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore // Verhindert Rekursion bei der Serialisierung
    private User user;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay = false; // Standardmäßig ganzer Tag

    @Column(length = 500)
    private String comment; // Optionaler Kommentar

    @Column(nullable = false)
    private LocalDate reportedAt; // Wann wurde die Krankheit gemeldet

    public SickLeave() {
        this.reportedAt = LocalDate.now();
    }

    // Getter und Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public boolean isHalfDay() { return halfDay; }
    public void setHalfDay(boolean halfDay) { this.halfDay = halfDay; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDate getReportedAt() { return reportedAt; }
    public void setReportedAt(LocalDate reportedAt) { this.reportedAt = reportedAt; }

    // Hilfsmethoden für die JSON-Antwort
    @JsonProperty("username")
    public String getUsername() {
        return (user != null) ? user.getUsername() : null;
    }

    @JsonProperty("color") // Eigene Farbe für Krankheitsanzeige im Kalender
    public String getColor() {
        // Beispiel: Eine Standardfarbe für Krankheitstage.
        // Du könntest dies auch pro User konfigurierbar machen, wenn gewünscht.
        return "#FF6347"; // Tomatenrot als Beispiel
    }
}