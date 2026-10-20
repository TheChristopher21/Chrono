package com.chrono.chrono.dto;

public class MobileAppFeedbackRequest {
    private String message;
    private String appMenuKey;
    private String appMenuTitle;
    private String appMenuGroup;
    private String appVersionName;
    private Integer appVersionCode;
    private String deviceInfo;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
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
}
