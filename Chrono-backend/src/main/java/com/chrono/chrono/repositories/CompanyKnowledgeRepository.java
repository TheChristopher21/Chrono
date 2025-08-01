package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.CompanyKnowledge;
import com.chrono.chrono.entities.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyKnowledgeRepository extends JpaRepository<CompanyKnowledge, Long> {
    List<CompanyKnowledge> findByCompany(Company company);
}
