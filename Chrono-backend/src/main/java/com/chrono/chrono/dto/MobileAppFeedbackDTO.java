package com.chrono.chrono.dto;

import com.chrono.chrono.entities.MobileAppFeedback;

import java.time.LocalDateTime;

public class MobileAppFeedbackDTO {
    private Long id;
    private Long companyId;
    private String companyName;
    private Long userId;
    private String username;
    private String displayName;
    private String appMenuKey;
    private String appMenuTitle;
    private String appMenuGroup;
    private String appVersionName;
    private Integer appVersionCode;
    private String deviceInfo;
    private String message;
    private LocalDateTime createdAt;

    public static MobileAppFeedbackDTO fromEntity(MobileAppFeedback feedback) {
        MobileAppFeedbackDTO dto = new MobileAppFeedbackDTO();
        dto.setId(feedback.getId());
        if (feedback.getCompany() != null) {
            dto.setCompanyId(feedback.getCompany().getId());
            dto.setCompanyName(feedback.getCompany().getName());
        }
        if (feedback.getUser() != null) {
            dto.setUserId(feedback.getUser().getId());
        }
        dto.setUsername(feedback.getUsername());
        dto.setDisplayName(feedback.getDisplayName());
        dto.setAppMenuKey(feedback.getAppMenuKey());
        dto.setAppMenuTitle(feedback.getAppMenuTitle());
        dto.setAppMenuGroup(feedback.getAppMenuGroup());
        dto.setAppVersionName(feedback.getAppVersionName());
        dto.setAppVersionCode(feedback.getAppVersionCode());
        dto.setDeviceInfo(feedback.getDeviceInfo());
        dto.setMessage(feedback.getMessage());
        dto.setCreatedAt(feedback.getCreatedAt());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAppMenuKey() { return appMenuKey; }
    public void setAppMenuKey(String appMenuKey) { this.appMenuKey = appMenuKey; }
    public String getAppMenuTitle() { return appMenuTitle; }
    public void setAppMenuTitle(String appMenuTitle) { this.appMenuTitle = appMenuTitle; }
    public String getAppMenuGroup() { return appMenuGroup; }
    public void setAppMenuGroup(String appMenuGroup) { this.appMenuGroup = appMenuGroup; }
    public String getAppVersionName() { return appVersionName; }
    public void setAppVersionName(String appVersionName) { this.appVersionName = appVersionName; }
    public Integer getAppVersionCode() { return appVersionCode; }
    public void setAppVersionCode(Integer appVersionCode) { this.appVersionCode = appVersionCode; }
    public String getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
