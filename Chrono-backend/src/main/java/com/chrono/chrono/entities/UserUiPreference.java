package com.chrono.chrono.entities;

import com.chrono.chrono.entities.pms.HotelProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_ui_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ui_pref_user_tenant_area_context",
                columnNames = {"user_id", "tenant_key", "area", "context_key"}
        ),
        indexes = {
                @Index(name = "idx_ui_pref_company_user_area", columnList = "company_id,user_id,area"),
                @Index(name = "idx_ui_pref_property", columnList = "property_id")
        }
)
public class UserUiPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private HotelProperty property;

    @Column(name = "tenant_key", nullable = false, length = 80)
    private String tenantKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserUiPreferenceArea area;

    @Column(name = "context_key", nullable = false, length = 80)
    private String contextKey;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Version
    @Column(nullable = false)
    private long revision;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public HotelProperty getProperty() { return property; }
    public void setProperty(HotelProperty property) { this.property = property; }
    public String getTenantKey() { return tenantKey; }
    public void setTenantKey(String tenantKey) { this.tenantKey = tenantKey; }
    public UserUiPreferenceArea getArea() { return area; }
    public void setArea(UserUiPreferenceArea area) { this.area = area; }
    public String getContextKey() { return contextKey; }
    public void setContextKey(String contextKey) { this.contextKey = contextKey; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
