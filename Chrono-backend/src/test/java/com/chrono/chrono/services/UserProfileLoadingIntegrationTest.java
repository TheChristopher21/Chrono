package com.chrono.chrono.services;

import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.CustomerRepository;
import com.chrono.chrono.repositories.RoleRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@Import({UserService.class, UserPermissionService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserProfileLoadingIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void persistProfileContextInASeparateTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Company company = new Company("Chrono Hotel");
            company.setCantonAbbreviation("ZH");
            company.setCustomerTrackingEnabled(true);
            company.setEnabledFeatures(Set.of("projects"));
            company = companyRepository.save(company);

            Customer customer = new Customer();
            customer.setName("Hotelkunde");
            customer.setCompany(company);
            customer = customerRepository.save(customer);

            Role role = roleRepository.save(new Role("ROLE_ADMIN"));

            User user = new User();
            user.setUsername("profile-user");
            user.setPassword("encoded-password");
            user.setCountry("CH");
            user.setPersonnelNumber("PROFILE-1");
            user.setCompany(company);
            user.setLastCustomer(customer);
            user.setRoles(Set.of(role));
            user.setPagePermissions(Map.of("pms", "MANAGE"));
            userRepository.save(user);
        });
    }

    @Test
    void buildsTheCurrentUserProfileWithOpenInViewDisabled() {
        UserDTO profile = userService.getUserProfileByUsername("profile-user");

        assertThat(profile.getUsername()).isEqualTo("profile-user");
        assertThat(profile.getCompanyId()).isNotNull();
        assertThat(profile.getCompanyCantonAbbreviation()).isEqualTo("ZH");
        assertThat(profile.getCustomerTrackingEnabled()).isTrue();
        assertThat(profile.getLastCustomerId()).isNotNull();
        assertThat(profile.getLastCustomerName()).isEqualTo("Hotelkunde");
        assertThat(profile.getRoles()).containsExactly("ROLE_ADMIN");
        assertThat(profile.getCompanyFeatureKeys()).contains("projects");
        assertThat(profile.getPagePermissions()).containsEntry("pms", "MANAGE");
    }
}
