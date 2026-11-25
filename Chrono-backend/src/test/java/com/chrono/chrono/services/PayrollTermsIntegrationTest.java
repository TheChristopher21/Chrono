package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@ActiveProfiles("test")
@Import({PayrollTermsService.class,PayrollTermsIntegrationTest.Json.class})
class PayrollTermsIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration static class Json { @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); } }
    @Autowired PayrollTermsService service;
    @Autowired CompanyRepository companies;
    @Autowired UserRepository users;
    @Autowired PayrollTermsRepository versions;
    final LocalDate january=LocalDate.of(2026,1,1);
    private User user() {
        var c=companies.saveAndFlush(new Company("Rules fixture"));
        var u=new User();u.setUsername("fixture-"+c.getId());u.setPersonnelNumber("fixture-"+c.getId());u.setPassword("test-only");u.setCountry("CH");u.setCompany(c);return users.saveAndFlush(u);
    }
    @Test void rulesAreTenantScopedVersionedAndNeverOverwriteCurrentOrHistoricalSalary() {
        var u=user(); u.setMonthlySalary(1234.56);users.saveAndFlush(u);
        var first=service.add(u.getCompany().getId(),u.getId(),new PayrollTermsService.Input(u.getId(),january,"Contract",PayrollTestTerms.swiss()));
        var future=service.add(u.getCompany().getId(),u.getId(),new PayrollTermsService.Input(u.getId(),january.plusMonths(1),"Raise",PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("amount","6000"))));
        assertEquals(first.getId(),service.resolve(u,january,january.plusDays(30)).versionId());
        assertEquals(future.getId(),service.resolve(u,january.plusMonths(1),january.plusMonths(1)).versionId());
        assertThrows(IllegalStateException.class,()->service.resolve(u,january,january.plusMonths(1)));
        assertEquals(1234.56,users.findById(u.getId()).orElseThrow().getMonthlySalary());assertEquals(2,versions.count());
        var other=user();assertTrue(service.list(other.getCompany().getId()).isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.add(other.getCompany().getId(),other.getId(),new PayrollTermsService.Input(u.getId(),january,"Cross tenant",PayrollTestTerms.swiss())));
    }
    @Test void missingInputsDoNotTurnIntoZeroOrUseTodaysUndatedUserData() {
        var u=user();assertThrows(IllegalStateException.class,()->service.resolve(u,january,january.plusDays(30)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.add(u.getCompany().getId(),u.getId(),new PayrollTermsService.Input(u.getId(),january,"Contract",PayrollTestTerms.change(PayrollTestTerms.swiss(),Map.of("weeklyHours","0")))));
        assertEquals(0,versions.count());
    }
}
