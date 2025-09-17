package com.chrono.chrono.services;

import com.chrono.chrono.dto.ProjectHierarchyNodeDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Task;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.ProjectRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

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

        User user = new User();
        user.setId(42L);

        Task billableTask = new Task();
        billableTask.setBillable(true);
        Task nonBillableTask = new Task();
        nonBillableTask.setBillable(false);

        LocalDateTime base = LocalDateTime.of(2024, 1, 5, 8, 0);

        TimeTrackingEntry rootStartBillable = new TimeTrackingEntry(user, customer, root, base,
                TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        rootStartBillable.setTask(billableTask);
        TimeTrackingEntry rootEndBillable = new TimeTrackingEntry(user, customer, root, base.plusMinutes(200),
                TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        rootEndBillable.setDurationMinutes(200);
        rootEndBillable.setTask(billableTask);

        TimeTrackingEntry rootStartNonBillable = new TimeTrackingEntry(user, customer, root, base.plusMinutes(200),
                TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        rootStartNonBillable.setTask(nonBillableTask);
        TimeTrackingEntry rootEndNonBillable = new TimeTrackingEntry(user, customer, root, base.plusMinutes(300),
                TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        rootEndNonBillable.setDurationMinutes(100);
        rootEndNonBillable.setTask(nonBillableTask);

        TimeTrackingEntry childStartBillable = new TimeTrackingEntry(user, customer, child, base.plusHours(6),
                TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        childStartBillable.setTask(billableTask);
        TimeTrackingEntry childEndBillable = new TimeTrackingEntry(user, customer, child, base.plusHours(7),
                TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        childEndBillable.setDurationMinutes(60);
        childEndBillable.setTask(billableTask);

        TimeTrackingEntry childStartNonBillable = new TimeTrackingEntry(user, customer, child, base.plusHours(7),
                TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        childStartNonBillable.setTask(nonBillableTask);
        TimeTrackingEntry childEndNonBillable = new TimeTrackingEntry(user, customer, child, base.plusHours(7).plusMinutes(30),
                TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH);
        childEndNonBillable.setDurationMinutes(30);
        childEndNonBillable.setTask(nonBillableTask);

        when(timeTrackingEntryRepository.findByCompanyIdAndEntryTimestampBetween(eq(companyId), any(), any()))
                .thenReturn(Stream.of(
                        rootStartBillable,
                        rootEndBillable,
                        rootStartNonBillable,
                        rootEndNonBillable,
                        childStartBillable,
                        childEndBillable,
                        childStartNonBillable,
                        childEndNonBillable
                ).toList());

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
