package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DemoDataService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TimeTrackingEntryRepository timeTrackingEntryRepository;

    @Transactional
    public void resetDemoData(User user) {
        // Remove existing demo data
        timeTrackingEntryRepository.deleteByUser(user);
        if (user.getCompany() != null) {
            Long companyId = user.getCompany().getId();
            projectRepository.deleteByCustomerCompanyId(companyId);
            customerRepository.deleteByCompanyId(companyId);
        }

        // Ensure company exists
        Company company = user.getCompany();
        if (company == null) {
            company = new Company("Demo Company");
            company = companyRepository.save(company);
            user.setCompany(company);
        }

        // Sample customer
        Customer customer = new Customer();
        customer.setName("Beispielkunde");
        customer.setCompany(user.getCompany());
        customer = customerRepository.save(customer);

        // Sample project
        Project project = new Project();
        project.setName("Beispielprojekt");
        project.setCustomer(customer);
        project = projectRepository.save(project);

        // Sample time entries
        LocalDateTime start = LocalDateTime.now().minusHours(2);
        TimeTrackingEntry startEntry = new TimeTrackingEntry(user, customer, project, start,
                TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        timeTrackingEntryRepository.save(startEntry);
        TimeTrackingEntry endEntry = new TimeTrackingEntry(user, customer, project, start.plusHours(1),
                TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        endEntry.setDurationMinutes(60);
        timeTrackingEntryRepository.save(endEntry);

        userRepository.save(user);
    }
}
