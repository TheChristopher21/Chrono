package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChatFeatureServiceTest {

    private final ChatFeatureService service = new ChatFeatureService();

    @Test
    void isChatbotEnabled_requiresCompanyFeatureForRegularUsers() {
        Company company = new Company();
        company.setEnabledFeatures(Set.of("crm"));
        User user = new User();
        user.setCompany(company);
        user.setRoles(Set.of(new Role("ROLE_USER")));

        assertThat(service.isChatbotEnabled(user)).isFalse();

        company.setEnabledFeatures(Set.of("crm", "chatbot"));

        assertThat(service.isChatbotEnabled(user)).isTrue();
    }

    @Test
    void isChatbotEnabled_allowsSuperadminForManagementAccess() {
        User user = new User();
        user.setRoles(Set.of(new Role("ROLE_SUPERADMIN")));

        assertThat(service.isChatbotEnabled(user)).isTrue();
    }
}
