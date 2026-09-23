package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserUiPreference;
import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.services.UiPreferencePayloadValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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

    @Autowired
    private EntityManager entityManager;

    @Test
    void restoresPmsGridJsonUnchangedAfterClearingPersistenceContext() throws Exception {
        Company company = companyRepository.save(new Company("Grid Hotel AG"));
        User user = userRepository.save(user("preference-grid", "PREF-GRID", company));
        HotelProperty property = new HotelProperty();
        property.setCompany(company);
        property.setCode("GRID");
        property.setName("Grid Hotel");
        entityManager.persist(property);
        String context = "property:" + property.getId();
        ObjectMapper mapper = new ObjectMapper();
        var payload = mapper.readTree("""
                {"type":"chrono-dashboard-layouts","schemaVersion":1,"layouts":{"%s":{"widgets":[
                  {"id":"occupancy","visible":true,"order":0,"size":"M","x":7,"y":176,"w":5,"h":24},
                  {"id":"legacy-widget","visible":false,"order":1,"size":"S"}
                ]}}}
                """.formatted(context));
        UiPreferencePayloadValidator validator = new UiPreferencePayloadValidator(mapper);
        UserUiPreference row = preference(user, company, context);
        row.setArea(UserUiPreferenceArea.PMS_DASHBOARD);
        row.setProperty(property);
        row.setPayload(validator.validateAndSerialize(UserUiPreferenceArea.PMS_DASHBOARD, context, payload));
        Long userId = user.getId();
        String tenantKey = row.getTenantKey();
        String serialized = row.getPayload();
        preferenceRepository.saveAndFlush(row);
        entityManager.clear();

        UserUiPreference restored = preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                userId, tenantKey, UserUiPreferenceArea.PMS_DASHBOARD, context).orElseThrow();

        assertThat(restored.getPayload()).isEqualTo(serialized);
        assertThat(validator.deserializeAndValidate(UserUiPreferenceArea.PMS_DASHBOARD, context,
                restored.getPayload())).isEqualTo(payload);
        assertThat(restored.getSchemaVersion()).isEqualTo(1);
    }

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
