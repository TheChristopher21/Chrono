package com.chrono.chrono.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class IntegrationTriggerResultDTO {
    private Long configId;
    private LocalDateTime triggeredAt;
    private String status;
    private int recordsSent;
    private Map<String, Object> payloadPreview;

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRecordsSent() {
        return recordsSent;
    }

    public void setRecordsSent(int recordsSent) {
        this.recordsSent = recordsSent;
    }

    public Map<String, Object> getPayloadPreview() {
        return payloadPreview;
    }

    public void setPayloadPreview(Map<String, Object> payloadPreview) {
        this.payloadPreview = payloadPreview;
    }
}
