package com.chrono.chrono.dto;

import com.chrono.chrono.entities.IntegrationConfig;

import java.time.LocalDateTime;

public class IntegrationConfigDTO {
    private Long id;
    private String name;
    private IntegrationConfig.IntegrationType type;
    private String endpointUrl;
    private String authHeader;
    private Boolean active;
    private Boolean autoSync;
    private LocalDateTime lastTriggeredAt;
    private String lastStatus;

    public IntegrationConfigDTO() {
    }

    public IntegrationConfigDTO(IntegrationConfig config) {
        this.id = config.getId();
        this.name = config.getName();
        this.type = config.getType();
        this.endpointUrl = config.getEndpointUrl();
        this.authHeader = config.getAuthHeader();
        this.active = config.getActive();
        this.autoSync = config.getAutoSync();
        this.lastTriggeredAt = config.getLastTriggeredAt();
        this.lastStatus = config.getLastStatus();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IntegrationConfig.IntegrationType getType() {
        return type;
    }

    public void setType(IntegrationConfig.IntegrationType type) {
        this.type = type;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getAuthHeader() {
        return authHeader;
    }

    public void setAuthHeader(String authHeader) {
        this.authHeader = authHeader;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getAutoSync() {
        return autoSync;
    }

    public void setAutoSync(Boolean autoSync) {
        this.autoSync = autoSync;
    }

    public LocalDateTime getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(LocalDateTime lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }
}
