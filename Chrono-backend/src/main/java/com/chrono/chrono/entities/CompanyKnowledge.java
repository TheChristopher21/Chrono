package com.chrono.chrono.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "company_knowledge")
public class CompanyKnowledge {

    public enum AccessLevel {
        ALL,
        ADMIN_ONLY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessLevel accessLevel = AccessLevel.ALL;

    public CompanyKnowledge() {}

    public CompanyKnowledge(Company company, String title, String content, AccessLevel level) {
        this.company = company;
        this.title = title;
        this.content = content;
        this.accessLevel = level;
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public AccessLevel getAccessLevel() { return accessLevel; }
    public void setAccessLevel(AccessLevel accessLevel) { this.accessLevel = accessLevel; }
}
