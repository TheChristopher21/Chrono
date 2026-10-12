package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.dto.UserDTO;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.security.CompanyScope;
import com.chrono.chrono.services.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.security.Principal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyScopedDashboardTest {
    final UserRepository users = mock(UserRepository.class);
    final UserPermissionService permissions = mock(UserPermissionService.class);
    final UserService userService = mock(UserService.class);
    final User actor = new User();
    final Principal principal = () -> "root";
    @BeforeEach void setup() {
        actor.setId(1L); actor.setUsername("root"); actor.setRoles(Set.of(new Role("ROLE_SUPERADMIN")));
        when(users.findByUsername("root")).thenReturn(Optional.of(actor));
        when(userService.getUserByUsername("root")).thenReturn(actor);
        select(20);
    }
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }
    void select(long id) {
        Company company = new Company(); company.setId(id); company.setName("Firma " + id);
        var request = new MockHttpServletRequest();
        request.setAttribute(CompanyScope.ATTRIBUTE, new CompanyScope.Selection("root", company));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    void field(Object target, String name, Object value) { ReflectionTestUtils.setField(target, name, value); }
    @Test void employeeListsChangeCompanyAndNeverLoadTheGlobalDirectory() {
        var controller = new AdminUserController(); field(controller,"userRepository",users); field(controller,"userPermissionService",permissions);
        field(controller,"employmentModelHistoryService",mock(EmploymentModelHistoryService.class));
        User alice = new User(); alice.setUsername("Alice");
        User bob = new User(); bob.setUsername("Bob");
        when(users.findOperationalUsersByCompanyIdAndDeletedFalse(20L)).thenReturn(List.of(alice));
        when(users.findOperationalUsersByCompanyIdAndDeletedFalse(30L)).thenReturn(List.of(bob));
        assertThat(controller.getAllUsers(principal).getBody()).extracting("username").containsExactly("Alice");
        select(30);
        assertThat(controller.getAllUsers(principal).getBody()).extracting("username").containsExactly("Bob");
        verify(users, never()).findOperationalUsersDeletedFalse();
        assertThat(actor.getCompany()).isNull();
    }
    @Test void timeSummariesAndBalancesUseOnlyTheSelectedCompany() {
        var controller = new AdminTimeTrackingController(); var times = mock(TimeTrackingService.class);
        field(controller,"userRepository",users); field(controller,"userPermissionService",permissions); field(controller,"timeTrackingService",times);
        when(users.findTimeOverviewUsersByCompanyIdAndDeletedFalse(20L)).thenReturn(List.of());
        assertThat(controller.getAllTimeTrackSummaries(principal).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.getAllCurrentTrackingBalances(principal).getStatusCode().value()).isEqualTo(200);
        verify(users,times(2)).findTimeOverviewUsersByCompanyIdAndDeletedFalse(20L);
        verify(users,never()).findTimeOverviewUsersDeletedFalse();
    }
    @Test void correctionInboxUsesTheSelectedCompany() {
        var controller = new CorrectionRequestController(); var corrections = mock(CorrectionRequestService.class);
        field(controller,"userRepo",users); field(controller,"userPermissionService",permissions); field(controller,"correctionRequestService",corrections);
        when(corrections.getRequestsByCompanyForAdminDashboard(20L)).thenReturn(List.of());
        assertThat(controller.getAllPendingRequests(principal).getStatusCode().value()).isEqualTo(200);
        verify(corrections).getRequestsByCompanyForAdminDashboard(20L);
        verify(corrections,never()).getAllRequestsForAdminDashboard();
    }
    @Test void vacationsAndSicknessDoNotMixCompaniesOrAcceptADifferentCompanyParameter() {
        var vacationController = new VacationController(); var vacations = mock(VacationService.class);
        field(vacationController,"userService",userService); field(vacationController,"userPermissionService",permissions); field(vacationController,"vacationService",vacations);
        vacationController.getAllVacations(principal);
        verify(vacations).getAllVacationsInCompany(20L); verify(vacations,never()).getAllVacations();
        var sickController = new SickLeaveController(); var sickness = mock(SickLeaveService.class);
        field(sickController,"userService",userService); field(sickController,"sickLeaveService",sickness);
        sickController.getAllSickLeavesInCompany(30L, principal);
        verify(sickness).getAllSickLeavesInCompany(20L, actor); verify(sickness,never()).getAllSickLeavesForSuperAdmin();
    }
    @Test void payrollListsUseTheSelectedCompany() {
        var payslips = mock(com.chrono.chrono.repositories.PayslipRepository.class);
        var payroll = new PayrollService();
        field(payroll,"payslipRepository",payslips);
        payroll.getAllPayslips(actor);
        verify(payslips).findByUser_Company_Id(20L); verify(payslips,never()).findAll();
    }

    @Test void userCreationAndCompanyTransfersCannotEscapeTheSelection() {
        var controller = new AdminUserController();
        var companies = mock(CompanyRepository.class);
        field(controller,"userRepository",users); field(controller,"companyRepository",companies);
        field(controller,"userPermissionService",permissions);
        Company other = new Company(); other.setId(30L);
        when(companies.findById(30L)).thenReturn(Optional.of(other));
        UserDTO input = new UserDTO(); input.setCompanyId(30L); input.setId(201L);
        assertThatThrownBy(() -> controller.addUser(input, principal))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        User own = new User(); own.setCompany(CompanyScope.selection().company());
        when(users.findById(201L)).thenReturn(Optional.of(own));
        assertThatThrownBy(() -> controller.updateUser(input, null, principal))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(own.getCompany().getId()).isEqualTo(20L);
        verify(users, never()).save(any());
    }

    @Test void directTimeEntryActionsCannotModifyAnotherCompany() {
        var entries = mock(TimeTrackingEntryRepository.class);
        var timeService = new TimeTrackingService();
        field(timeService,"userRepository",users); field(timeService,"timeTrackingEntryRepository",entries);
        Company other = new Company(); other.setId(30L);
        User target = new User(); target.setCompany(other);
        TimeTrackingEntry entry = new TimeTrackingEntry(); entry.setUser(target);
        when(entries.findById(301L)).thenReturn(Optional.of(entry));
        assertThatThrownBy(() -> timeService.approveEntry(301L, "root"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> timeService.updateEntryProject(301L, null, "root"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(entries, never()).save(any());
    }
}
