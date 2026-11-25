package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.CompanyKnowledge;
import com.chrono.chrono.entities.Customer;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.Task;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.GlobalExceptionHandler;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.CompanyKnowledgeService;
import com.chrono.chrono.services.ComplianceAuditService;
import com.chrono.chrono.services.CustomerService;
import com.chrono.chrono.services.ProjectService;
import com.chrono.chrono.services.TaskService;
import com.chrono.chrono.services.TimeTrackingService;
import com.chrono.chrono.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreateTenantIdentityMvcTest {
    private static final String[] ENDPOINTS = {"/api/customers", "/api/projects", "/api/tasks", "/api/admin/knowledge"};
    private final CustomerService customers = mock(CustomerService.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final TaskService tasks = mock(TaskService.class);
    private final CompanyKnowledgeService knowledge = mock(CompanyKnowledgeService.class);
    private final ComplianceAuditService audit = mock(ComplianceAuditService.class);
    private final UserService users = mock(UserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final Company ownCompany = company(1L);
    private final Company otherCompany = company(2L);
    private final Customer ownCustomer = customer(11L, ownCompany, "Own customer");
    private final Customer otherCustomer = customer(21L, otherCompany, "Other customer");
    private final Project ownProject = project(12L, ownCustomer, "Own project");
    private final Project otherProject = project(22L, otherCustomer, "Other project");
    private final Task otherTask = new Task();
    private final CompanyKnowledge otherDocument = new CompanyKnowledge(otherCompany, "Other title", "Other content", CompanyKnowledge.AccessLevel.ALL);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        otherTask.setId(23L);
        otherTask.setName("Other task");
        otherTask.setProject(otherProject);
        ReflectionTestUtils.setField(otherDocument, "id", 24L);
        User alice = user("alice", ownCompany);
        User bob = user("bob", otherCompany);
        when(users.getUserByUsername("alice")).thenReturn(alice);
        when(users.getUserByUsername("bob")).thenReturn(bob);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(companies.findById(1L)).thenReturn(Optional.of(ownCompany));
        when(companies.findById(2L)).thenReturn(Optional.of(otherCompany));
        when(customers.findById(11L)).thenReturn(Optional.of(ownCustomer));
        when(customers.findById(21L)).thenReturn(Optional.of(otherCustomer));
        when(projects.findById(12L)).thenReturn(Optional.of(ownProject));
        when(projects.findById(22L)).thenReturn(Optional.of(otherProject));

        CustomerController customerController = new CustomerController();
        ReflectionTestUtils.setField(customerController, "customerService", customers);
        ReflectionTestUtils.setField(customerController, "userService", users);
        ReflectionTestUtils.setField(customerController, "timeTrackingService", mock(TimeTrackingService.class));
        ProjectController projectController = new ProjectController();
        ReflectionTestUtils.setField(projectController, "projectService", projects);
        ReflectionTestUtils.setField(projectController, "customerService", customers);
        ReflectionTestUtils.setField(projectController, "userService", users);
        ReflectionTestUtils.setField(projectController, "complianceAuditService", audit);
        TaskController taskController = new TaskController();
        ReflectionTestUtils.setField(taskController, "taskService", tasks);
        ReflectionTestUtils.setField(taskController, "projectService", projects);
        ReflectionTestUtils.setField(taskController, "userService", users);
        AdminKnowledgeController knowledgeController = new AdminKnowledgeController();
        ReflectionTestUtils.setField(knowledgeController, "knowledgeService", knowledge);
        ReflectionTestUtils.setField(knowledgeController, "userRepository", userRepository);
        ReflectionTestUtils.setField(knowledgeController, "companyRepository", companies);
        mvc = MockMvcBuilders.standaloneSetup(customerController, projectController, taskController, knowledgeController)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    static Stream<Arguments> identityBodies() {
        return Stream.of(ENDPOINTS).flatMap(endpoint -> {
            long foreignId = switch (endpoint) {
                case "/api/customers" -> 21;
                case "/api/projects" -> 22;
                case "/api/tasks" -> 23;
                default -> 24;
            };
            return Stream.of("\"id\":" + foreignId, "\"id\":\"" + foreignId + "\"", "\"id\":null",
                            "\"id\":{\"value\":" + foreignId + "}", "\"id\":null,\"id\":" + foreignId,
                            "\"\\u0069d\":" + foreignId)
                    .map(identity -> Arguments.of(endpoint, identity));
        });
    }

    @ParameterizedTest
    @MethodSource("identityBodies")
    void rejectsForeignIdentityAndAlternateJsonFormsBeforeAnySave(String endpoint, String identity) throws Exception {
        String fields = fields(endpoint, 11L, 12L);
        mvc.perform(post(endpoint).principal(() -> "alice").contentType(MediaType.APPLICATION_JSON)
                        .content("{" + identity + "," + fields + "}"))
                .andExpect(status().isBadRequest());

        verify(customers, never()).save(any());
        verify(projects, never()).save(any());
        verify(tasks, never()).save(any());
        verify(knowledge, never()).save(any());
        verifyNoInteractions(audit);
        assertOtherCompanyUnchanged();
    }

    static Stream<Arguments> normalCreates() {
        return Stream.of(ENDPOINTS).flatMap(endpoint -> Stream.of("alice", "bob").map(username -> Arguments.of(endpoint, username)));
    }

    @ParameterizedTest
    @MethodSource("normalCreates")
    void createsFreshEntitiesWithAuthorizedRelationsAndExistingResponses(String endpoint, String username) throws Exception {
        boolean alice = username.equals("alice");
        Company company = alice ? ownCompany : otherCompany;
        Customer customer = alice ? ownCustomer : otherCustomer;
        Project parent = alice ? ownProject : otherProject;
        when(customers.save(any())).thenAnswer(invocation -> {
            Customer created = invocation.getArgument(0);
            assertNull(created.getId());
            assertSame(company, created.getCompany());
            assertEquals("New", created.getName());
            created.setId(101L);
            return created;
        });
        when(projects.save(any())).thenAnswer(invocation -> {
            Project created = invocation.getArgument(0);
            assertNull(created.getId());
            assertSame(customer, created.getCustomer());
            assertSame(parent, created.getParent());
            assertEquals(90, created.getBudgetMinutes());
            assertEquals(new BigDecimal("125.50"), created.getHourlyRate());
            assertTrue(created.getChildren().isEmpty());
            created.setId(101L);
            return created;
        });
        when(tasks.save(any())).thenAnswer(invocation -> {
            Task created = invocation.getArgument(0);
            assertNull(created.getId());
            assertSame(parent, created.getProject());
            assertEquals(90, created.getBudgetMinutes());
            assertEquals(Boolean.TRUE, created.getBillable());
            created.setId(101L);
            return created;
        });
        when(knowledge.save(any())).thenAnswer(invocation -> {
            CompanyKnowledge created = invocation.getArgument(0);
            assertNull(created.getId());
            assertSame(company, created.getCompany());
            assertEquals("New", created.getTitle());
            assertEquals("Content", created.getContent());
            assertEquals(CompanyKnowledge.AccessLevel.ADMIN_ONLY, created.getAccessLevel());
            ReflectionTestUtils.setField(created, "id", 101L);
            return created;
        });

        mvc.perform(post(endpoint).principal(() -> username).contentType(MediaType.APPLICATION_JSON)
                        .content("{" + fields(endpoint, customer.getId(), parent.getId()) + "}"))
                .andExpect(status().is(endpoint.equals("/api/customers") ? 201 : 200))
                .andExpect(jsonPath("$.id").value(101));
        assertOtherCompanyUnchanged();
    }

    @Test
    void rejectsCrossCompanyCustomerParentAndTaskProjectReferences() throws Exception {
        for (String body : new String[]{
                "{\"name\":\"New\",\"customer\":{\"id\":21}}",
                "{\"name\":\"New\",\"customer\":{\"id\":11},\"parent\":{\"id\":22}}"}) {
            mvc.perform(post("/api/projects").principal(() -> "alice").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/tasks").principal(() -> "alice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"project\":{\"id\":22}}"))
                .andExpect(status().isForbidden());
        verify(projects, never()).save(any());
        verify(tasks, never()).save(any());
        assertOtherCompanyUnchanged();
    }

    @Test
    void relationshipPayloadCannotInjectNestedEntitiesOrChangeKnowledgeDefaults() throws Exception {
        when(projects.save(any())).thenAnswer(invocation -> {
            Project created = invocation.getArgument(0);
            assertNull(created.getId());
            assertSame(ownCustomer, created.getCustomer());
            assertEquals("Own customer", created.getCustomer().getName());
            assertTrue(created.getChildren().isEmpty());
            return created;
        });
        mvc.perform(post("/api/projects").principal(() -> "alice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"customer\":{\"id\":11,\"name\":\"Injected\",\"company\":{\"id\":2}},\"children\":[{\"id\":22}]}"))
                .andExpect(status().isOk());
        when(knowledge.save(any())).thenAnswer(invocation -> {
            CompanyKnowledge created = invocation.getArgument(0);
            assertNull(created.getId());
            assertSame(ownCompany, created.getCompany());
            assertEquals(CompanyKnowledge.AccessLevel.ALL, created.getAccessLevel());
            return created;
        });
        mvc.perform(post("/api/admin/knowledge").principal(() -> "alice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\",\"content\":\"Content\",\"company\":{\"id\":2}}"))
                .andExpect(status().isOk());
        assertOtherCompanyUnchanged();
    }

    private void assertOtherCompanyUnchanged() {
        assertEquals("Other customer", otherCustomer.getName());
        assertSame(otherCompany, otherCustomer.getCompany());
        assertEquals("Other project", otherProject.getName());
        assertSame(otherCustomer, otherProject.getCustomer());
        assertEquals("Other task", otherTask.getName());
        assertSame(otherProject, otherTask.getProject());
        assertEquals("Other title", otherDocument.getTitle());
        assertEquals("Other content", otherDocument.getContent());
        assertSame(otherCompany, otherDocument.getCompany());
    }

    private static String fields(String endpoint, Long customerId, Long projectId) {
        return switch (endpoint) {
            case "/api/customers" -> "\"name\":\"New\"";
            case "/api/projects" -> "\"name\":\"New\",\"customer\":{\"id\":" + customerId + "},\"parent\":{\"id\":" + projectId + "},\"budgetMinutes\":90,\"hourlyRate\":125.50";
            case "/api/tasks" -> "\"name\":\"New\",\"project\":{\"id\":" + projectId + "},\"budgetMinutes\":90,\"billable\":true";
            default -> "\"title\":\"New\",\"content\":\"Content\",\"accessLevel\":\"ADMIN_ONLY\"";
        };
    }

    private static Company company(long id) {
        Company company = new Company();
        company.setId(id);
        company.setCustomerTrackingEnabled(true);
        return company;
    }

    private static User user(String username, Company company) {
        User user = new User();
        user.setUsername(username);
        user.setCompany(company);
        return user;
    }

    private static Customer customer(long id, Company company, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCompany(company);
        customer.setName(name);
        return customer;
    }

    private static Project project(long id, Customer customer, String name) {
        Project project = new Project();
        project.setId(id);
        project.setCustomer(customer);
        project.setName(name);
        return project;
    }
}
