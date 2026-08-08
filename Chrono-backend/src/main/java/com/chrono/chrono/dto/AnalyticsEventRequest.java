package com.chrono.chrono.dto;

public class AnalyticsEventRequest {
    private String eventType;
    private String visitorId;
    private String sessionId;
    private String path;
    private String pageTitle;
    private String referrer;
    private String elementLabel;
    private String elementTarget;
    private String language;
    private Integer viewportWidth;
    private Integer viewportHeight;

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
}
