package com.chrono.chrono.entities;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "schedule_rules")
public class ScheduleRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shiftKey;

    // Null ownership is reserved for immutable, pre-migration shared defaults.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @JsonIgnore
    @Column(nullable = false)
    private boolean suppressed;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String startTime;

    @Column(nullable = false)
    private String endTime;

    private boolean isActive = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShiftKey() { return shiftKey; }
    public void setShiftKey(String shiftKey) { this.shiftKey = shiftKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public boolean getIsActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    @JsonIgnore
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    @JsonIgnore
    public boolean isSuppressed() { return suppressed; }
    public void setSuppressed(boolean suppressed) { this.suppressed = suppressed; }
}
