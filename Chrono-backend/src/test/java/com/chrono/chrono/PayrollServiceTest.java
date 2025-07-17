package com.chrono.chrono;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.PayrollService;
import com.chrono.chrono.services.EmailService;
import com.chrono.chrono.services.PdfService;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({PayrollService.class, PayrollServiceTest.TestConfig.class})
public class PayrollServiceTest {
    @Autowired
    private PayrollService payrollService;
    @Autowired
    private UserRepository userRepository;

    @Test
    public void testGeneratePayslip() {
        User u = new User();
        u.setUsername("t");
        u.setPassword("p");
        u.setHourlyRate(10.0);
        userRepository.save(u);
        var ps = payrollService.generatePayslip(u.getId(), LocalDate.now().minusDays(10), LocalDate.now());
        assertNotNull(ps.getGrossSalary());
        assertTrue(ps.getNetSalary() <= ps.getGrossSalary());
        payrollService.approvePayslip(ps.getId(), null);
        var saved = payrollService.getAllPayslips().get(0);
        assertTrue(saved.isLocked());
        assertNotNull(saved.getPdfPath());
    }
}

@Configuration
class TestConfig {
    @Bean
    public JavaMailSender javaMailSender() {
        return new org.springframework.mail.javamail.JavaMailSenderImpl();
    }

    @Bean
    public EmailService emailService(JavaMailSender sender) {
        return new EmailService(sender);
    }

    @Bean
    public PdfService pdfService() {
        return new PdfService();
    }
}
