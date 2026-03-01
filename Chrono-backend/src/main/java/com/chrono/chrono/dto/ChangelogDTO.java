package com.chrono.chrono.dto;

import java.time.Instant;

public class ChangelogDTO {

    private Long id;
    private String version;
    private String title;
    private String content;
    private Instant createdAt;

    // --- Constructors, Getters and Setters ---

    public ChangelogDTO() {
    }

    public ChangelogDTO(Long id, String version, String title, String content, Instant createdAt) {
        this.id = id;
        this.version = version;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
    }

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