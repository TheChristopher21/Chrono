package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    java.util.List<Project> findByCustomerCompanyId(Long companyId);

    void deleteByCustomerCompanyId(Long companyId);
}
