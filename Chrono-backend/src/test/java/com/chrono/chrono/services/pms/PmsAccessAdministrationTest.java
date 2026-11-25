package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.PmsPropertyGrant;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.PmsPropertyGrantRepository;
import com.chrono.chrono.services.UserPermissionService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
@Import({PmsPropertyAccessService.class, UserPermissionService.class, PmsAuditWriter.class})
class PmsAccessAdministrationTest {
    @Autowired PmsPropertyAccessService service;
    @Autowired CompanyRepository companies;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired HotelPropertyRepository properties;
    @Autowired PmsPropertyGrantRepository grants;
    @Autowired EntityManager entityManager;
    Company company;
    Role staffRole;

    @BeforeEach void seed() {
        company = new Company("Hotel Group"); company.setEnabledFeatures(Set.of("pms"));
        company = companies.save(company);
        staffRole = roles.save(new Role("ROLE_USER"));
        Role masterRole = roles.save(new Role("ROLE_ADMIN"));
        user(company, "master", masterRole);
        HotelProperty property = new HotelProperty();
        property.setCompany(company); property.setName("Hotel Alpha"); property.setCode("A");
        property = properties.save(property);
        for (int index = 0; index < 12; index++) {
            User user = user(company, "staff_%02d".formatted(index), staffRole);
            PmsPropertyGrant grant = new PmsPropertyGrant();
            grant.setUser(user); grant.setProperty(property); grant.setPermissions(Map.of("FRONT_DESK", "VIEW"));
            grants.save(grant);
        }
        User deleted = user(company, "staff_deleted", staffRole); deleted.setDeleted(true);
        Company other = companies.save(new Company("Another tenant"));
        user(other, "staff_foreign", staffRole);
        entityManager.flush(); entityManager.clear();
    }

    @Test void returnsStableBoundedPagesWithOnlyActiveTenantUsers() {
        var first = service.administration("master", 0, 5, "STAFF");
        var second = service.administration("master", 1, 5, "staff");
        var last = service.administration("master", 2, 5, "staff");
        assertThat(first.totalElements()).isEqualTo(12);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.users()).extracting(entry -> entry.username())
                .containsExactly("staff_00", "staff_01", "staff_02", "staff_03", "staff_04");
        assertThat(second.users()).extracting(entry -> entry.username())
                .containsExactly("staff_05", "staff_06", "staff_07", "staff_08", "staff_09");
        assertThat(last.users()).hasSize(2);
        assertThat(last.hasNext()).isFalse();
        assertThat(first.users()).allSatisfy(entry -> {
            assertThat(entry.master()).isFalse();
            assertThat(entry.grants()).singleElement().satisfies(grant ->
                    assertThat(grant.permissions()).containsEntry("FRONT_DESK", "VIEW"));
        });
        assertThat(first.properties()).singleElement().satisfies(property -> assertThat(property.propertyName()).isEqualTo("Hotel Alpha"));
    }

    @Test void treatsWildcardCharactersLiterallyAndSearchesFullNames() {
        User special = user(company, "discount%_desk", staffRole);
        special.setFirstName("Ada"); special.setLastName("Lovelace");
        entityManager.flush(); entityManager.clear();
        assertThat(service.administration("master", 0, 10, "%_").users())
                .extracting(entry -> entry.username()).containsExactly("discount%_desk");
        assertThat(service.administration("master", 0, 10, "ADA LOVELACE").users())
                .extracting(entry -> entry.username()).containsExactly("discount%_desk");
        assertThat(service.administration("master", 3, 10, "staff").users()).isEmpty();
    }

    @Test void databaseQueryCountDoesNotGrowPerEmployeeOrGrant() {
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        service.administration("master", 0, 2, "staff");
        long smallPageQueries = statistics.getPrepareStatementCount();
        entityManager.clear(); statistics.clear();
        service.administration("master", 0, 10, "staff");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(smallPageQueries).isLessThanOrEqualTo(7);
    }

    @Test void rejectsUnboundedPagesAndNonMasterAccess() {
        assertThatThrownBy(() -> service.administration("master", 0, 101, "")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.administration("master", -1, 20, "")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.administration("staff_00", 0, 20, "")).isInstanceOf(ResponseStatusException.class);
    }

    private User user(Company owner, String name, Role role) {
        User user = new User(); user.setUsername(name); user.setPassword("test-only");
        user.setCountry("CH"); user.setPersonnelNumber(name); user.setCompany(owner);
        user.setRoles(Set.of(role)); user.setPagePermissions(Map.of("pms", "MANAGE"));
        return users.save(user);
    }
}
