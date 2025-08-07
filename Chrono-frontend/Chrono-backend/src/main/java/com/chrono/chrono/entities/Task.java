package com.chrono.chrono.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column
    private Boolean billable;

    @Column(name = "budget_minutes")
    private Integer budgetMinutes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }

    public Integer getBudgetMinutes() { return budgetMinutes; }
    public void setBudgetMinutes(Integer budgetMinutes) { this.budgetMinutes = budgetMinutes; }
}
