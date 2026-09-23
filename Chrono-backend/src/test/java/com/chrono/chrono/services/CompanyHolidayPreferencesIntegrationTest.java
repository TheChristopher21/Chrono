package com.chrono.chrono.services;

import com.chrono.chrono.dto.CompanyHolidayPreferenceDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.CompanyHolidayPreference;
import com.chrono.chrono.repositories.CompanyHolidayPreferenceRepository;
import com.chrono.chrono.repositories.CompanyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@Import(HolidayService.class)
class CompanyHolidayPreferencesIntegrationTest {

    @Autowired
    private HolidayService holidayService;
    @Autowired
    private CompanyRepository companies;
    @Autowired
    private CompanyHolidayPreferenceRepository preferences;
    @Autowired
    private EntityManager entityManager;

    @Test
    void replacesExistingSelectionAndAllowsRepeatedSavesWithoutUniqueConflict() {
        Company company = companies.saveAndFlush(new Company("Chrono AG"));
        Company otherCompany = companies.saveAndFlush(new Company("Other AG"));
        preferences.saveAllAndFlush(List.of(
                new CompanyHolidayPreference(company, "CH_NEUJAHR", false),
                new CompanyHolidayPreference(company, "CH_BERCHTOLDSTAG", false),
                new CompanyHolidayPreference(otherCompany, "CH_NEUJAHR", false)
        ));
        List<CompanyHolidayPreferenceDTO> selection = List.of(
                new CompanyHolidayPreferenceDTO("CH_NEUJAHR", true),
                new CompanyHolidayPreferenceDTO("CH_KARFREITAG", false)
        );

        holidayService.replaceCompanyHolidayPreferences(company, selection);
        entityManager.flush();
        entityManager.clear();
        holidayService.replaceCompanyHolidayPreferences(company, selection);
        entityManager.flush();
        entityManager.clear();

        assertThat(preferences.findByCompany_Id(company.getId()))
                .extracting(CompanyHolidayPreference::getHolidayCode, CompanyHolidayPreference::isHalfDay)
                .containsExactlyInAnyOrder(tuple("CH_NEUJAHR", true), tuple("CH_KARFREITAG", false));
        assertThat(preferences.findByCompany_Id(otherCompany.getId()))
                .extracting(CompanyHolidayPreference::getHolidayCode, CompanyHolidayPreference::isHalfDay)
                .containsExactly(tuple("CH_NEUJAHR", false));

        holidayService.replaceCompanyHolidayPreferences(company, List.of());
        entityManager.flush();
        entityManager.clear();
        assertThat(preferences.findByCompany_Id(company.getId())).isEmpty();
        assertThat(preferences.findByCompany_Id(otherCompany.getId())).hasSize(1);
    }
}
