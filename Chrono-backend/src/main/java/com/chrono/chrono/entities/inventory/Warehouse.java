package com.chrono.chrono.entities.inventory;

import com.chrono.chrono.entities.Company;
import jakarta.persistence.*;

@Entity
@Table(
        name = "inv_warehouses",
        uniqueConstraints = @UniqueConstraint(name = "uk_inv_warehouses_company_code", columnNames = {"company_id", "code"}),
        indexes = @Index(name = "idx_inv_warehouses_company", columnList = "company_id")
)
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 512)
    private String location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }
}
