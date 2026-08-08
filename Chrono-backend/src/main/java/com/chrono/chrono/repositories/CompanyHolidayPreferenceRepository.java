package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.CompanyHolidayPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyHolidayPreferenceRepository extends JpaRepository<CompanyHolidayPreference, Long> {
    List<CompanyHolidayPreference> findByCompany_Id(Long companyId);
    void deleteByCompany_Id(Long companyId);
}
