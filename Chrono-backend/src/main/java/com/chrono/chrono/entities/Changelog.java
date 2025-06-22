package com.chrono.chrono.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "changelog")
public class Changelog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) // Jede Version sollte einzigartig sein
    private String version;

    @Column(nullable = false)
    private String title;

    @Lob // Definiert ein "Large Object", ideal für längeren Text
    @Column(nullable = false, length = 5000)
    private String content;

    @CreationTimestamp // Setzt den Zeitstempel automatisch beim Erstellen
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // --- Getter and Setter ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}