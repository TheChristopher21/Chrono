package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.CompanyKnowledge;
import com.chrono.chrono.repositories.CompanyKnowledgeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompanyKnowledgeService {

    @Autowired
    private CompanyKnowledgeRepository repo;

    public List<CompanyKnowledge> findByCompany(Company company) {
        return repo.findByCompany(company);
    }

    public CompanyKnowledge save(CompanyKnowledge doc) {
        return repo.save(doc);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
