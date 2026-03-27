package com.chrono.chrono.services;

import com.chrono.chrono.dto.ChatRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.CompanyKnowledge;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private RestTemplate longTimeoutRestTemplate;
    @Mock
    private CompanyKnowledgeService companyKnowledgeService;
    @Mock
    private VacationService vacationService;
    @Mock
    private UserRepository userRepository;

    private ChatService chatService;
    private User admin;
    private User employee;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                restTemplate,
                longTimeoutRestTemplate,
                companyKnowledgeService,
                vacationService,
                userRepository
        );
        ReflectionTestUtils.setField(chatService, "llmBaseUrl", "http://llm.test/api/generate");
        ReflectionTestUtils.setField(chatService, "modelName", "llama3:8b");

        Company company = new Company();
        company.setId(1L);
        company.setName("Chrono GmbH");

        admin = new User();
        admin.setUsername("boss");
        admin.setCompany(company);
        admin.setRoles(Set.of(new Role("ROLE_ADMIN")));

        employee = new User();
        employee.setUsername("max");
        employee.setCompany(company);
        employee.setRoles(Set.of(new Role("ROLE_USER")));
        employee.setAnnualVacationDays(30);
        employee.setTrackingBalanceInMinutes(125);
    }

    @Test
    void ask_returnsDetailedVacationForMentionedUser_whenRequesterIsAdmin() {
        when(userRepository.findByCompany_IdAndDeletedFalse(1L)).thenReturn(List.of(employee));
        when(vacationService.calculateRemainingVacationDays("max", LocalDate.now().getYear())).thenReturn(18.5);

        String answer = chatService.ask("Wie viel Urlaub hat max?", admin);

        assertThat(answer).contains("max");
        assertThat(answer).contains("Resturlaub");
        assertThat(answer).contains("18,5");
        verify(restTemplate, never()).postForObject(anyString(), any(), eq(String.class));
    }

    @Test
    void ask_returnsVacationOverview_whenAdminAsksGenericVacationQuestion() {
        User second = new User();
        second.setUsername("zoe");
        second.setCompany(admin.getCompany());

        when(userRepository.findByCompany_IdAndDeletedFalse(1L)).thenReturn(List.of(employee, second));
        when(vacationService.calculateRemainingVacationDays("max", LocalDate.now().getYear())).thenReturn(10.0);
        when(vacationService.calculateRemainingVacationDays("zoe", LocalDate.now().getYear())).thenReturn(21.0);

        String answer = chatService.ask("Wie sieht unser Urlaub im Team aus?", admin);

        assertThat(answer).contains("Resturlaub");
        assertThat(answer).contains("max");
        assertThat(answer).contains("zoe");
    }

    @Test
    void ask_returnsOvertimeForSelf_withoutLlmCall() {
        User normalUser = new User();
        normalUser.setUsername("anna");
        normalUser.setTrackingBalanceInMinutes(90);

        String answer = chatService.ask("Wie viele Ueberstunden habe ich?", normalUser);

        assertThat(answer).contains("1:30");
        verify(restTemplate, never()).postForObject(anyString(), any(), eq(String.class));
    }

    @Test
    void ask_includesHistoryAndGeneralKnowledgeInstruction_whenLlmIsUsed() {
        when(companyKnowledgeService.findByCompany(admin.getCompany())).thenReturn(List.of());
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Paris ist die Hauptstadt von Frankreich.\"}");

        List<ChatRequest.ChatMessage> history = List.of(
                new ChatRequest.ChatMessage("user", "Wir sprechen ueber Europa."),
                new ChatRequest.ChatMessage("bot", "Klar, frag mich etwas dazu.")
        );

        String answer = chatService.ask("Was ist die Hauptstadt von Frankreich?", history, admin);

        assertThat(answer).contains("Paris");

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) requestCaptor.getValue().getBody();
        String prompt = (String) body.get("prompt");

        assertThat(prompt).contains("allgemeine Wissensfragen ausserhalb von Chrono");
        assertThat(prompt).contains("Wir sprechen ueber Europa.");
        assertThat(prompt).contains("Was ist die Hauptstadt von Frankreich?");
    }

    @Test
    void ask_keepsAdminOnlyKnowledgeOutOfPrompt_forRegularUsers() {
        CompanyKnowledge publicDoc = new CompanyKnowledge(
                employee.getCompany(),
                "Urlaubsprozess",
                "Alle Mitarbeitenden beantragen Urlaub im Portal.",
                CompanyKnowledge.AccessLevel.ALL
        );
        CompanyKnowledge adminDoc = new CompanyKnowledge(
                employee.getCompany(),
                "Budgetfreigaben",
                "Nur Admins sehen interne Budgetgrenzen.",
                CompanyKnowledge.AccessLevel.ADMIN_ONLY
        );

        when(companyKnowledgeService.findByCompany(employee.getCompany())).thenReturn(List.of(publicDoc, adminDoc));
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"Nutze das Portal.\"}");

        chatService.ask("Wie funktioniert unser Urlaubsprozess?", List.of(), employee);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) requestCaptor.getValue().getBody();
        String prompt = (String) body.get("prompt");

        assertThat(prompt).contains("Alle Mitarbeitenden beantragen Urlaub im Portal.");
        assertThat(prompt).doesNotContain("Nur Admins sehen interne Budgetgrenzen.");
    }
}
