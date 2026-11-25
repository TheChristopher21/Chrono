package com.chrono.chrono.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "mobile_app_feedback", indexes = {
        @Index(name = "idx_mobile_feedback_created", columnList = "created_at"),
        @Index(name = "idx_mobile_feedback_company_created", columnList = "company_id, created_at")
})
public class MobileAppFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 120)
    private String username;

    @Column(length = 180)
    private String displayName;

    @Column(name = "app_menu_key", length = 80)
    private String appMenuKey;

    @Column(name = "app_menu_title", length = 120)
    private String appMenuTitle;

    @Column(name = "app_menu_group", length = 120)
    private String appMenuGroup;

    @Column(name = "app_version_name", length = 40)
    private String appVersionName;

    @Column(name = "app_version_code")
    private Integer appVersionCode;

    @Column(name = "device_info", length = 300)
    private String deviceInfo;

    @Lob
    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
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
