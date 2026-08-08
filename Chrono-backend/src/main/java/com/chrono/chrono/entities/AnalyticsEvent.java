package com.chrono.chrono.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "analytics_events", indexes = {
        @Index(name = "idx_analytics_created_type", columnList = "created_at,event_type"),
        @Index(name = "idx_analytics_path_created", columnList = "path,created_at"),
        @Index(name = "idx_analytics_visitor_created", columnList = "visitor_id,created_at")
})
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "visitor_id", nullable = false, length = 64)
    private String visitorId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(name = "page_title", length = 256)
    private String pageTitle;

    @Column(length = 512)
    private String referrer;

    @Column(name = "referrer_host", length = 255)
    private String referrerHost;

    @Column(name = "element_label", length = 160)
    private String elementLabel;

    @Column(name = "element_target", length = 512)
    private String elementTarget;

    @Column(length = 32)
    private String language;

    @Column(name = "viewport_width")
    private Integer viewportWidth;

    @Column(name = "viewport_height")
    private Integer viewportHeight;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(String visitorId) {
        this.visitorId = visitorId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public String getReferrerHost() {
        return referrerHost;
    }

    public void setReferrerHost(String referrerHost) {
        this.referrerHost = referrerHost;
    }

    public String getElementLabel() {
        return elementLabel;
    }

    public void setElementLabel(String elementLabel) {
        this.elementLabel = elementLabel;
    }

    public String getElementTarget() {
        return elementTarget;
    }

    public void setElementTarget(String elementTarget) {
        this.elementTarget = elementTarget;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getViewportWidth() {
        return viewportWidth;
    }

    public void setViewportWidth(Integer viewportWidth) {
        this.viewportWidth = viewportWidth;
    }

    public Integer getViewportHeight() {
        return viewportHeight;
    }

    public void setViewportHeight(Integer viewportHeight) {
        this.viewportHeight = viewportHeight;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
