package com.chrono.chrono.services;

import com.chrono.chrono.entities.IntegrationConfig;
import com.chrono.chrono.repositories.IntegrationConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class IntegrationConfigService {

    @Autowired
    private IntegrationConfigRepository integrationConfigRepository;

    public List<IntegrationConfig> findByCompanyId(Long companyId) {
        return integrationConfigRepository.findByCompanyId(companyId);
    }

    public Optional<IntegrationConfig> findById(Long id) {
        return integrationConfigRepository.findById(id);
    }

    public IntegrationConfig save(IntegrationConfig config) {
        return integrationConfigRepository.save(config);
    }

    public void delete(Long id) {
        integrationConfigRepository.deleteById(id);
    }
}
