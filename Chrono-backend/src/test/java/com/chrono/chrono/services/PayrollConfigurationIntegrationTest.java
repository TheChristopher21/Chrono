package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.PayrollConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@Import(PayrollConfigurationService.class)
class PayrollConfigurationIntegrationTest {
    @Autowired PayrollConfigurationService service;
    @Autowired PayrollConfigurationRepository versions;
    @Autowired CompanyRepository companies;
    @Autowired UserRepository users;
    private final LocalDate january = LocalDate.of(2026, 1, 1);
    private final BigDecimal zero = BigDecimal.ZERO;

    private User employee(Company company) {
        User user = new User(); user.setCompany(company); user.setUsername("person-" + company.getId());
        user.setPassword("test-only"); user.setCountry("CH"); user.setPersonnelNumber("p-" + company.getId());
        return users.saveAndFlush(user);
    }
    private PayrollConfigurationService.InsuranceInput insurance(LocalDate date, BigDecimal bu) {
        return new PayrollConfigurationService.InsuranceInput(date,"Testpolice",bu,zero,zero,zero,zero,zero,zero,zero);
    }
    @Test void distinguishesMissingFromExplicitZeroAndNeverUsesUndatedLegacyRates() {
        Company company = new Company("A"); company.setUvgBuRate(0.012); company = companies.saveAndFlush(company);
        User user = employee(company);
        assertThat(service.resolve(user,january,january.plusDays(30)).insurance()).isNull();
        service.addInsurance(company.getId(),user.getId(),insurance(january,null));
        assertThat(service.resolve(user,january,january.plusDays(30)).insuranceComplete()).isFalse();
        service.addInsurance(company.getId(),user.getId(),insurance(january,zero));
        service.addPension(company.getId(),user.getId(),new PayrollConfigurationService.PensionInput(user.getId(),january,"Kein BVG-Beitrag",zero,zero));
        var coverage = service.resolve(user,january,january.plusDays(30));
        assertThat(coverage.insuranceComplete()).isTrue(); assertThat(coverage.pensionComplete()).isTrue();
        assertThat(coverage.insurance().getUvgBuRate()).isEqualByComparingTo(zero);
        assertThat(versions.findByCompanyIdOrderByEffectiveFromDescIdDesc(company.getId())).hasSize(3);
        assertThat(company.getUvgBuRate()).isEqualTo(0.012);
    }
    @Test void usesEffectiveDatesAndPreservesEarlierVersionsIncludingSameDateCorrections() {
        Company company = companies.saveAndFlush(new Company("A")); User user = employee(company);
        var first = service.addInsurance(company.getId(),user.getId(),insurance(january,new BigDecimal("0.01")));
        var future = service.addInsurance(company.getId(),user.getId(),insurance(january.plusMonths(1),new BigDecimal("0.02")));
        assertThat(service.resolve(user,january,january.plusDays(30)).insurance().getId()).isEqualTo(first.getId());
        assertThat(service.resolve(user,january,january.plusMonths(1)).insuranceChangesWithinPeriod()).isTrue();
        var corrected = service.addInsurance(company.getId(),user.getId(),insurance(january.plusMonths(1),new BigDecimal("0.025")));
        assertThat(service.resolve(user,january.plusMonths(1),january.plusMonths(1)).insurance().getId()).isEqualTo(corrected.getId());
        assertThat(versions.findById(future.getId()).orElseThrow().getUvgBuRate()).isEqualByComparingTo("0.02");
        assertThat(service.resolve(user,january.minusDays(1),january.minusDays(1)).insurance()).isNull();
    }
    @Test void scopesReadsAndRejectsPensionWritesForAnotherCompany() {
        Company a = companies.saveAndFlush(new Company("A")), b = companies.saveAndFlush(new Company("B"));
        User userA = employee(a), userB = employee(b);
        service.addInsurance(b.getId(),userB.getId(),insurance(january,zero));
        assertThat(service.list(a.getId()).versions()).isEmpty();
        assertThat(service.list(a.getId()).employees()).extracting(PayrollConfigurationService.Employee::id).containsExactly(userA.getId());
        assertThatThrownBy(() -> service.addPension(a.getId(),userA.getId(),new PayrollConfigurationService.PensionInput(userB.getId(),january,"Ausweis",zero,zero)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }
    @Test void rejectsNegativeRatesWithoutSaving() {
        assertThatThrownBy(() -> service.addInsurance(1L,1L,insurance(january,new BigDecimal("-0.01"))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThat(versions.count()).isZero();
    }
    @Test void rejectsPrecisionLossInsteadOfSilentlyRoundingRates() {
        assertThatThrownBy(() -> service.addInsurance(1L,1L,insurance(january,new BigDecimal("0.012345678"))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThat(versions.count()).isZero();
    }
}
