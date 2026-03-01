package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    // optional: Company findByName(String name);
}
