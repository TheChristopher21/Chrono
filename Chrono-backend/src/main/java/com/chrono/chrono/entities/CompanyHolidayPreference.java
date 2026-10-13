package com.chrono.chrono.entities;

import jakarta.persistence.*;

@Entity
@Table(
        name = "company_holiday_preferences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "holiday_code"})
)
public class CompanyHolidayPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "holiday_code", nullable = false, length = 80)
    private String holidayCode;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay = false;

    public CompanyHolidayPreference() {}

    public CompanyHolidayPreference(Company company, String holidayCode, boolean halfDay) {
        this.company = company;
        this.holidayCode = holidayCode;
        this.halfDay = halfDay;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public String getHolidayCode() { return holidayCode; }
    public void setHolidayCode(String holidayCode) { this.holidayCode = holidayCode; }

    public boolean isHalfDay() { return halfDay; }
    public void setHalfDay(boolean halfDay) { this.halfDay = halfDay; }
}
