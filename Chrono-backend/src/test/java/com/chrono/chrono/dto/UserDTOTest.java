package com.chrono.chrono.dto;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserDTOTest {

    @Test
    void exposesModernProjectsFeatureThroughLegacyAndModernProfileFields() {
        Company company = new Company("Modern AG");
        company.setCustomerTrackingEnabled(false);
        company.setEnabledFeatures(Set.of("projects"));
        User user = new User();
        user.setCompany(company);

        UserDTO dto = new UserDTO(user);

        assertThat(dto.getCustomerTrackingEnabled()).isTrue();
        assertThat(dto.getCompanyFeatureKeys()).contains("projects");
    }

    @Test
    void exposesLegacyProjectFlagAsModernProfileFeature() {
        Company company = new Company("Legacy AG");
        company.setCustomerTrackingEnabled(true);
        company.setEnabledFeatures(Set.of());
        User user = new User();
        user.setCompany(company);

        UserDTO dto = new UserDTO(user);

        assertThat(dto.getCustomerTrackingEnabled()).isTrue();
        assertThat(dto.getCompanyFeatureKeys()).contains("projects");
    }
}
