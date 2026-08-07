package com.chrono.chrono.entities.listeners;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserAuditRepository;
import com.chrono.chrono.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserAuditListenerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditRepository userAuditRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void writesAuditThroughTheCurrentApplicationContext() {
        User user = new User();
        user.setUsername("audit-listener-user");
        user.setPassword("test-only-password");
        user.setCountry("CH");
        user.setPersonnelNumber("AUDIT-1");
        user.setEmail("before@example.test");
        Long userId = userRepository.saveAndFlush(user).getId();

        entityManager.clear();
        User managedUser = userRepository.findById(userId).orElseThrow();
        managedUser.setEmail("after@example.test");
        entityManager.flush();
        entityManager.clear();

        assertThat(userAuditRepository.findAll()).anySatisfy(audit -> {
            assertThat(audit.getUser().getId()).isEqualTo(userId);
            assertThat(audit.getFieldName()).isEqualTo("email");
            assertThat(audit.getOldValue()).isEqualTo("before@example.test");
            assertThat(audit.getNewValue()).isEqualTo("after@example.test");
        });
    }
}
