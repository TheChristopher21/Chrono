package com.chrono.chrono.services;

import com.chrono.chrono.dto.ProjectHierarchyNodeDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Task;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.CustomerRepository;
import com.chrono.chrono.repositories.ProjectRepository;
import com.chrono.chrono.repositories.TaskRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "SPRING_DATASOURCE_URL=jdbc:h2:mem:chrono_report_test;DB_CLOSE_DELAY=-1;MODE=MYSQL",
        "SPRING_DATASOURCE_USERNAME=sa",
        "SPRING_DATASOURCE_PASSWORD=",
        "SPRING_MAIL_HOST=localhost",
        "SPRING_MAIL_PORT=2525",
        "SPRING_MAIL_USERNAME=test",
        "SPRING_MAIL_PASSWORD=test",
        "spring.datasource.url=jdbc:h2:mem:chrono_report_test;DB_CLOSE_DELAY=-1;MODE=MYSQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Transactional
class ReportServiceIntegrationTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimeTrackingEntryRepository timeTrackingEntryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void projectAnalyticsIncludesTrackedMinutesFromPunches() {
        Company company = new Company();
        company.setName("TestCo");
        company.setCustomerTrackingEnabled(true);
        company = companyRepository.save(company);

        Customer customer = new Customer();
        customer.setName("Customer");
        customer.setCompany(company);
        customer = customerRepository.save(customer);

        Project project = new Project();
        project.setName("Project A");
        project.setCustomer(customer);
        project.setBudgetMinutes(600);
        project = projectRepository.save(project);

        User user = new User();
        user.setUsername("alice");
        user.setPassword("secret");
        user.setCountry("DE");
        user.setPersonnelNumber("PN-1");
        user.setCompany(company);
        user = userRepository.save(user);

        LocalDateTime startTime = LocalDateTime.of(2024, 1, 10, 8, 0);
        LocalDateTime endTime = startTime.plusHours(8);

        TimeTrackingEntry startEntry = new TimeTrackingEntry(user, customer, project, startTime,
                TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        timeTrackingEntryRepository.save(startEntry);

        TimeTrackingEntry endEntry = new TimeTrackingEntry(user, customer, project, endTime,
                TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        endEntry.setDurationMinutes(480);
        timeTrackingEntryRepository.save(endEntry);

        List<ProjectHierarchyNodeDTO> analytics = reportService.getProjectAnalytics(
                company.getId(),
                LocalDate.of(2024, 1, 10),
                LocalDate.of(2024, 1, 10)
        );

        Project finalProject = project;
        assertThat(analytics)
                .hasSize(1)
                .first()
                .satisfies(node -> {
                    assertThat(node.getId()).isEqualTo(finalProject.getId());
                    assertThat(node.getTotalMinutes()).isEqualTo(480L);
                    assertThat(node.getUtilization()).isNotNull();
                });
    }

    @Test
    void projectAnalyticsCountsEntriesAssignedViaTaskProject() {
        Company company = new Company();
        company.setName("TaskCo");
        company.setCustomerTrackingEnabled(true);
        company = companyRepository.save(company);

        Customer customer = new Customer();
        customer.setName("Customer");
        customer.setCompany(company);
        customer = customerRepository.save(customer);

        Project project = new Project();
        project.setName("Project B");
        project.setCustomer(customer);
        project.setBudgetMinutes(300);
        project = projectRepository.save(project);

        Task task = new Task();
        task.setName("Implementation");
        task.setProject(project);
        task.setBillable(true);
        task = taskRepository.save(task);

        User user = new User();
        user.setUsername("bob");
        user.setPassword("secret");
        user.setCountry("DE");
        user.setPersonnelNumber("PN-2");
        user.setCompany(company);
        user = userRepository.save(user);

        LocalDateTime startTime = LocalDateTime.of(2024, 2, 5, 9, 0);
        LocalDateTime endTime = startTime.plusMinutes(90);

        TimeTrackingEntry startEntry = new TimeTrackingEntry(user, customer, null, startTime,
                TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        startEntry.setTask(task);
        timeTrackingEntryRepository.save(startEntry);

        TimeTrackingEntry endEntry = new TimeTrackingEntry(user, customer, null, endTime,
                TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        endEntry.setTask(task);
        endEntry.setDurationMinutes(90);
        timeTrackingEntryRepository.save(endEntry);

        List<ProjectHierarchyNodeDTO> analytics = reportService.getProjectAnalytics(
                company.getId(),
                LocalDate.of(2024, 2, 5),
                LocalDate.of(2024, 2, 5)
        );

        Project finalProject = project;
        assertThat(analytics)
                .hasSize(1)
                .first()
                .satisfies(node -> {
                    assertThat(node.getId()).isEqualTo(finalProject.getId());
                    assertThat(node.getTotalMinutes()).isEqualTo(90L);
                    assertThat(node.getBillableMinutes()).isEqualTo(90L);
                });
    }
}

