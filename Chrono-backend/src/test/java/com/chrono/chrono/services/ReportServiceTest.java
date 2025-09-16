package com.chrono.chrono.services;

import com.chrono.chrono.dto.ProjectHierarchyNodeDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.repositories.ProjectRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private TimeTrackingService timeTrackingService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TimeTrackingEntryRepository timeTrackingEntryRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    void getProjectAnalytics_aggregatesHierarchyDurations() {
        Long companyId = 5L;

        Company company = new Company();
        company.setId(companyId);

        Customer customer = new Customer();
        customer.setCompany(company);

        Project root = new Project();
        root.setId(1L);
        root.setName("Root");
        root.setCustomer(customer);
        root.setBudgetMinutes(600);

        Project child = new Project();
        child.setId(2L);
        child.setName("Child");
        child.setCustomer(customer);
        child.setParent(root);
        child.setBudgetMinutes(120);

        when(projectRepository.findByCustomerCompanyIdOrderByNameAsc(companyId))
                .thenReturn(List.of(root, child));

        when(timeTrackingEntryRepository.sumDurationByProject(eq(companyId), any(), any()))
                .thenReturn(List.of(new Object[]{1L, 300L}, new Object[]{2L, 90L}));

        when(timeTrackingEntryRepository.sumBillableDurationByProject(eq(companyId), any(), any()))
                .thenReturn(List.of(new Object[]{1L, 200L}, new Object[]{2L, 60L}));

        List<ProjectHierarchyNodeDTO> analytics = reportService.getProjectAnalytics(
                companyId,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31)
        );

        assertEquals(1, analytics.size());
        ProjectHierarchyNodeDTO rootNode = analytics.get(0);
        assertEquals(1L, rootNode.getId());
        assertEquals(390L, rootNode.getTotalMinutes());
        assertEquals(260L, rootNode.getBillableMinutes());
        assertNotNull(rootNode.getUtilization());
        assertEquals(0.65, rootNode.getUtilization(), 0.0001);
        assertEquals(1, rootNode.getChildren().size());

        ProjectHierarchyNodeDTO childNode = rootNode.getChildren().get(0);
        assertEquals(2L, childNode.getId());
        assertEquals(90L, childNode.getTotalMinutes());
        assertEquals(60L, childNode.getBillableMinutes());
        assertEquals(0.75, childNode.getUtilization(), 0.0001);
    }
}
