package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserUiPreference;
import com.chrono.chrono.entities.UserUiPreferenceArea;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class UserUiPreferenceRepositoryTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUiPreferenceRepository preferenceRepository;

    @Test
    void persistsSelfScopedPreferenceAndAdvancesOptimisticRevision() {
        Company company = companyRepository.save(new Company("Chrono AG"));
        User user = userRepository.save(user("preference-user", "PREF-1", company));

        UserUiPreference preference = preference(user, company, "workspace");
        preference = preferenceRepository.saveAndFlush(preference);

        assertThat(preference.getRevision()).isZero();
        assertThat(preference.getCreatedAt()).isNotNull();
        assertThat(preference.getUpdatedAt()).isNotNull();
        assertThat(preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                user.getId(),
                "company:" + company.getId(),
                UserUiPreferenceArea.APP_TABS,
                "workspace"
        )).contains(preference);

        LocalDateTime previousUpdate = preference.getUpdatedAt();
        preference.setPayload("{\"tabs\":[],\"activeTabId\":null}");
        preference = preferenceRepository.saveAndFlush(preference);

        assertThat(preference.getRevision()).isEqualTo(1L);
        assertThat(preference.getUpdatedAt()).isAfterOrEqualTo(previousUpdate);
    }

    @Test
    void enforcesOneRowPerUserTenantAreaAndContext() {
        Company company = companyRepository.save(new Company("Chrono AG"));
        User user = userRepository.save(user("preference-unique", "PREF-2", company));
        preferenceRepository.saveAndFlush(preference(user, company, "workspace"));

        assertThatThrownBy(() -> preferenceRepository.saveAndFlush(
                preference(user, company, "workspace")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String username, String personnelNumber, Company company) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("encoded-password");
        user.setCountry("CH");
        user.setPersonnelNumber(personnelNumber);
        user.setCompany(company);
        return user;
    }

    private UserUiPreference preference(User user, Company company, String context) {
        UserUiPreference preference = new UserUiPreference();
        preference.setUser(user);
        preference.setCompany(company);
        preference.setTenantKey("company:" + company.getId());
        preference.setArea(UserUiPreferenceArea.APP_TABS);
        preference.setContextKey(context);
        preference.setSchemaVersion(1);
        preference.setPayload("{\"tabs\":[]}");
        return preference;
    }
}
