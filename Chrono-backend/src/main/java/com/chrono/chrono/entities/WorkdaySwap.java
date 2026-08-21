package com.chrono.chrono.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "workday_swaps",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_workday_swaps_user_original", columnNames = {"user_id", "original_work_date"}),
                @UniqueConstraint(name = "uk_workday_swaps_user_replacement", columnNames = {"user_id", "replacement_work_date"})
        }
)
public class WorkdaySwap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_work_date", nullable = false)
    private LocalDate originalWorkDate;

    @Column(name = "replacement_work_date", nullable = false)
    private LocalDate replacementWorkDate;

    @Column(name = "transferred_minutes", nullable = false)
    private Integer transferredMinutes;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDate getOriginalWorkDate() { return originalWorkDate; }
    public void setOriginalWorkDate(LocalDate originalWorkDate) { this.originalWorkDate = originalWorkDate; }
    public LocalDate getReplacementWorkDate() { return replacementWorkDate; }
    public void setReplacementWorkDate(LocalDate replacementWorkDate) { this.replacementWorkDate = replacementWorkDate; }
    public Integer getTransferredMinutes() { return transferredMinutes; }
    public void setTransferredMinutes(Integer transferredMinutes) { this.transferredMinutes = transferredMinutes; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
