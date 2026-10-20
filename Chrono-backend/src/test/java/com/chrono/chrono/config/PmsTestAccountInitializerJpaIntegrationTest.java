package com.chrono.chrono.config;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PmsTestAccountInitializerJpaIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void persistExistingPmsTestUserInASeparateTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Company company = companyRepository.save(new Company("Local PMS Test"));
            Role adminRole = roleRepository.save(new Role("ROLE_ADMIN"));

            User user = new User();
            user.setUsername("Christopher");
            user.setPassword("existing-password-hash");
            user.setCountry("CH");
            user.setPersonnelNumber("PMS-LOCAL");
            user.setCompany(company);
            user.setRoles(Set.of(adminRole));
            userRepository.save(user);
        });
    }

    @Test
    void initializesExistingUserWithDetachedCompanyContext() {
        PmsTestAccountInitializer initializer = new PmsTestAccountInitializer(
                userRepository,
                roleRepository,
                companyRepository,
                mock(PasswordEncoder.class),
                new UserPermissionService()
        );
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "username", "Christopher");
        ReflectionTestUtils.setField(initializer, "password", "");

        assertThatCode(initializer::run).doesNotThrowAnyException();

        User initializedUser = userRepository.findByUsernameWithPermissionContext("Christopher")
                .orElseThrow();
        assertThat(initializedUser.getCompany().getEnabledFeatures()).contains("pms");
        assertThat(initializedUser.getPagePermissions())
                .containsEntry(UserPermissionService.PAGE_PMS, UserPermissionService.ACCESS_MANAGE);
    }
}
