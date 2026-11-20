package com.chrono.chrono.dto;

import com.chrono.chrono.entities.CompanyKnowledge.AccessLevel;

public class CreateCompanyKnowledgeRequest extends CreateRequest {
    private String title;
    private String content;
    private AccessLevel accessLevel = AccessLevel.ALL;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public AccessLevel getAccessLevel() { return accessLevel; }
    public void setAccessLevel(AccessLevel accessLevel) { this.accessLevel = accessLevel; }
}
