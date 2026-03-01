package com.chrono.chrono.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.CompanyKnowledge;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.CompanyKnowledgeService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Value("${llm.base-url}")
    private String llmBaseUrl;

    @Value("${llama.model:llama3:8b}")
    private String modelName;

    private final RestTemplate restTemplate;
    private final RestTemplate longTimeoutRestTemplate; // The special patient RestTemplate
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CompanyKnowledgeService companyKnowledgeService;
    private final VacationService vacationService;
    private final UserRepository userRepository;
    private String knowledgeBaseContent = "";
    private static final List<String> FALLBACKS = Arrays.asList(
            "Das habe ich leider nicht im Repertoire, aber ich lerne gerne dazu! Versuche es gerne nochmal anders oder schau in die Hilfeseite.",
            "Puh, diese Frage ist echt knifflig! Magst du sie noch mal anders formulieren – oder ich leite dich an den Support weiter?",
            "Da muss ich passen – aber vielleicht findest du Hilfe im Menü unter 'Hilfe & FAQ'.",
            "Hier stoße ich an meine Grenzen – aber keine Sorge, du kannst immer auch deinen Admin oder den Support kontaktieren!",
            "Sorry, das weiß ich leider nicht, aber ich bin immer neugierig auf neue Themen!",
            "Das ist spannend – aber da bin ich leider überfragt. Vielleicht kann dir der technische Support helfen.",
            "Diese Antwort habe ich gerade nicht parat. Probiere es nochmal oder frage nach Support.",
            "Du hast mich erwischt – das weiß ich (noch) nicht. Aber zusammen finden wir sicher eine Lösung!",
            "Gute Frage! Im Moment kann ich darauf nicht antworten, aber ich kann dich an einen echten Menschen weiterleiten."
    );

    // Modified constructor to accept both RestTemplates and the knowledge service
    public ChatService(RestTemplate restTemplate,
                       @Qualifier("longTimeoutRestTemplate") RestTemplate longTimeoutRestTemplate,
                       CompanyKnowledgeService companyKnowledgeService,
                       VacationService vacationService,
                       UserRepository userRepository) {
        this.restTemplate = restTemplate;
        this.longTimeoutRestTemplate = longTimeoutRestTemplate;
        this.companyKnowledgeService = companyKnowledgeService;
        this.vacationService = vacationService;
        this.userRepository = userRepository;
        loadKnowledgeBaseFromResources();
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpLlm() {
        logger.info("Starte LLM Warm-up im Hintergrund... Dies kann einige Minuten dauern.");
        try {
            // This request will trigger the model download if needed.
            // Ollama will handle this automatically. We use the patient RestTemplate here.
            String response = askWithTemplate(longTimeoutRestTemplate, "Hallo, initialisiere dich bitte.", null, -1);

            if (response != null && !response.contains("Fehler")) {
                logger.info("LLM Warm-up erfolgreich. Modell ist jetzt geladen und wird dauerhaft gehalten.");
            } else {
                logger.warn("LLM Warm-up mit einer Fehlerantwort abgeschlossen.");
            }
        } catch (Exception e) {
            logger.error("Fehler während des LLM Warm-ups. Der Server könnte unter starker Last stehen oder das Modell ist sehr gross.", e);
        }
    }

    private void loadKnowledgeBaseFromResources() {
        // ... (this method remains unchanged)
        logger.info("Lade Wissensdatenbank aus den Anwendungs-Ressourcen...");
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:knowledge_base/*.md");

            this.knowledgeBaseContent = Arrays.stream(resources)
                    .map(resource -> {
                        try (InputStream inputStream = resource.getInputStream()) {
                            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            logger.error("Fehler beim Lesen der Ressource: {}", resource.getFilename(), e);
                            return "";
                        }
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            if (knowledgeBaseContent.isEmpty()) {
                logger.warn("Keine Wissens-Dateien im 'resources/knowledge_base' Ordner gefunden.");
            } else {
                logger.info("Wissensdatenbank erfolgreich geladen ({} Zeichen).", this.knowledgeBaseContent.length());
            }
        } catch (IOException e) {
            logger.error("Fehler beim Suchen nach Wissens-Dateien.", e);
        }
    }

    // Public method for chat requests, uses the STANDARD RestTemplate
    public String ask(String message, User user) {
        if (user != null && isAdmin(user)) {
            String adminAnswer = answerAdminDataQuestion(message, user);
            if (adminAnswer != null) {
                return adminAnswer;
            }
        }

        if (message != null) {
            String lower = message.toLowerCase();
            Map<String, String> links = new HashMap<>();
            links.put("login", "/login");
            links.put("anmeldung", "/login");
            links.put("profil", "/profile");
            links.put("zeiterfassung", "/user");
            links.put("abrechnung", "/payslips");
            links.put("projekte", "/admin/projects");
            links.put("kunden", "/admin/customers");
            links.put("aufgaben", "/admin/tasks");

            for (Map.Entry<String, String> e : links.entrySet()) {
                if (lower.contains("wo") && lower.contains(e.getKey())) {
                    String label = e.getKey().substring(0, 1).toUpperCase() + e.getKey().substring(1);
                    return "Den Bereich '" + label + "' erreichst du über [" + label + "](" +
                            e.getValue() + ") im Menü.";

                }
            }
        }

        return askWithTemplate(this.restTemplate, message, user, 0);
    }

    // Private core method, now accepts a RestTemplate instance
    private String askWithTemplate(RestTemplate template, String message, User user, Number keepAliveDuration) {
        if (user != null && message != null && message.toLowerCase().contains("überstunden")) {
            int minutes = user.getTrackingBalanceInMinutes() != null ? user.getTrackingBalanceInMinutes() : 0;
            int hours = Math.abs(minutes) / 60;
            int mins = Math.abs(minutes) % 60;
            String sign = minutes < 0 ? "-" : "";
            return String.format("Du hast aktuell %s%d:%02d Stunden.", sign, hours, mins);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String userContext = (user != null)
                    ? "Der Benutzer ist eingeloggt mit dem Benutzernamen '" + user.getUsername() + "'."
                    : "Es ist kein Benutzer eingeloggt.";

            String companyKnowledge = getCompanyKnowledgeForUser(user);
            String adminContext = buildAdminOperationalContext(user, message);
            String fullPrompt = "Antworte auf die folgende Frage nur basierend auf dem untenstehenden Kontext. Antworte in der gleichen Sprache wie die Frage.\n\n" +
                    "Benutzerkontext: " + userContext + "\n\n" +
                    "--- KONTEXT ---\n" +
                    this.knowledgeBaseContent +
                    companyKnowledge +
                    adminContext +
                    "\n--- FRAGE ---\n" +
                    message;

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("prompt", fullPrompt);
            body.put("stream", false);

            // The keep_alive is now only really needed for the first warm-up call
            if (keepAliveDuration.doubleValue() != 0) {
                body.put("keep_alive", keepAliveDuration);
            }

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            logger.info("Sende Anfrage an Ollama mit Modell {}...", modelName);
            String jsonResponse = template.postForObject(llmBaseUrl, requestEntity, String.class);
            logger.info("Antwort von Ollama erhalten.");

            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            if (rootNode.has("response")) {
                return rootNode.get("response").asText();
            }

        } catch (Exception e) {
            logger.error("Fehler bei der Kommunikation mit dem LLM:", e);
        }
        return getRandomFallback();
    }

    private String getRandomFallback() {
        return FALLBACKS.get(ThreadLocalRandom.current().nextInt(FALLBACKS.size()));
    }

    private String getCompanyKnowledgeForUser(User user) {
        if (user == null || user.getCompany() == null) {
            return "";
        }
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_SUPERADMIN"));
        List<CompanyKnowledge> docs = companyKnowledgeService.findByCompany(user.getCompany());
        StringBuilder sb = new StringBuilder();
        for (CompanyKnowledge d : docs) {
            if (d.getAccessLevel() == CompanyKnowledge.AccessLevel.ALL || isAdmin) {
                sb.append("\n\n---\n\n");
                sb.append(d.getTitle()).append("\n");
                sb.append(d.getContent());
            }
        }
        return sb.toString();
    }

    private String answerAdminDataQuestion(String message, User requester) {
        if (message == null || requester.getCompany() == null) {
            return null;
        }

        String normalized = normalize(message);
        boolean asksVacation = normalized.contains("urlaub") || normalized.contains("vacation") || normalized.contains("ferientag");
        boolean asksOvertime = normalized.contains("uberstunden") || normalized.contains("ueberstunden") || normalized.contains("overtime");

        if (!asksVacation && !asksOvertime) {
            return null;
        }

        List<User> companyUsers = getCompanyUsersForAdmin(requester);
        if (companyUsers.isEmpty()) {
            return "Ich konnte keine Mitarbeitenden für deine Firma finden.";
        }

        Optional<User> explicitTarget = findMentionedUser(companyUsers, normalized);
        if (explicitTarget.isPresent()) {
            return buildDetailedUserSummary(explicitTarget.get(), asksVacation, asksOvertime);
        }

        if (asksVacation) {
            return buildCompanyVacationOverview(companyUsers);
        }

        return buildCompanyOvertimeOverview(companyUsers);
    }

    private List<User> getCompanyUsersForAdmin(User admin) {
        if (admin.getCompany() == null || admin.getCompany().getId() == null) {
            return List.of();
        }
        List<User> users = userRepository.findByCompany_IdAndDeletedFalse(admin.getCompany().getId());
        if (!users.isEmpty()) {
            return users;
        }
        return userRepository.findByCompany_Id(admin.getCompany().getId());
    }

    private Optional<User> findMentionedUser(List<User> users, String normalizedMessage) {
        return users.stream()
                .filter(u -> {
                    String username = normalize(u.getUsername());
                    return !username.isBlank() && normalizedMessage.contains(username);
                })
                .findFirst();
    }

    private String buildDetailedUserSummary(User target, boolean includeVacation, boolean includeOvertime) {
        int currentYear = LocalDate.now().getYear();
        StringBuilder sb = new StringBuilder();
        sb.append("Für '").append(target.getUsername()).append("': ");

        if (includeVacation) {
            double remaining = vacationService.calculateRemainingVacationDays(target.getUsername(), currentYear);
            int annual = target.getAnnualVacationDays() != null ? target.getAnnualVacationDays() : 25;
            sb.append(String.format(Locale.GERMAN,
                    "Resturlaub %d: %.1f Tage (von %d). ",
                    currentYear,
                    remaining,
                    annual));
        }

        if (includeOvertime) {
            int minutes = target.getTrackingBalanceInMinutes() != null ? target.getTrackingBalanceInMinutes() : 0;
            int hours = Math.abs(minutes) / 60;
            int mins = Math.abs(minutes) % 60;
            String sign = minutes < 0 ? "-" : "";
            sb.append("Überstunden-Saldo: ").append(sign).append(hours).append(":")
                    .append(String.format("%02d", mins)).append(" Stunden.");
        }

        return sb.toString().trim();
    }

    private String buildCompanyVacationOverview(List<User> users) {
        int currentYear = LocalDate.now().getYear();
        List<User> sorted = users.stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .limit(20)
                .toList();

        String summary = sorted.stream()
                .map(u -> {
                    double remaining = vacationService.calculateRemainingVacationDays(u.getUsername(), currentYear);
                    return String.format(Locale.GERMAN, "%s: %.1f Tage", u.getUsername(), remaining);
                })
                .collect(Collectors.joining(" | "));

        return "Hier ist der Resturlaub für " + currentYear + " (Top " + sorted.size() + " Mitarbeitende): " + summary +
                ". Tipp: Frag z. B. 'Wie viel Urlaub hat USERNAME?', dann gebe ich dir Details.";
    }

    private String buildCompanyOvertimeOverview(List<User> users) {
        List<User> sorted = users.stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .limit(20)
                .toList();

        String summary = sorted.stream()
                .map(u -> {
                    int minutes = u.getTrackingBalanceInMinutes() != null ? u.getTrackingBalanceInMinutes() : 0;
                    int hours = Math.abs(minutes) / 60;
                    int mins = Math.abs(minutes) % 60;
                    String sign = minutes < 0 ? "-" : "";
                    return String.format("%s: %s%d:%02d h", u.getUsername(), sign, hours, mins);
                })
                .collect(Collectors.joining(" | "));

        return "Hier ist der Überstunden-Saldo (Top " + sorted.size() + " Mitarbeitende): " + summary +
                ". Tipp: Frag z. B. 'Wie viele Überstunden hat USERNAME?' für Details.";
    }

    private String buildAdminOperationalContext(User user, String message) {
        if (user == null || !isAdmin(user) || user.getCompany() == null || message == null) {
            return "";
        }

        String normalized = normalize(message);
        boolean wantsOperationalInsight = normalized.contains("team") || normalized.contains("mitarbeiter")
                || normalized.contains("company") || normalized.contains("firma")
                || normalized.contains("urlaub") || normalized.contains("uberstunden")
                || normalized.contains("ueberstunden") || normalized.contains("status")
                || normalized.contains("wer") || normalized.contains("who");

        if (!wantsOperationalInsight) {
            return "";
        }

        List<User> users = getCompanyUsersForAdmin(user).stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .limit(25)
                .toList();

        if (users.isEmpty()) {
            return "";
        }

        int currentYear = LocalDate.now().getYear();
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- ADMIN-OPERATIVER KONTEXT (AKTUELL) ---\n")
                .append("Firma: ").append(user.getCompany().getName() != null ? user.getCompany().getName() : "Unbekannt").append("\n")
                .append("Mitarbeitende: ").append(users.size()).append("\n");

        for (User companyUser : users) {
            double remainingVacation = 0.0;
            try {
                remainingVacation = vacationService.calculateRemainingVacationDays(companyUser.getUsername(), currentYear);
            } catch (Exception e) {
                logger.debug("Konnte Resturlaub für {} nicht berechnen: {}", companyUser.getUsername(), e.getMessage());
            }

            int minutes = companyUser.getTrackingBalanceInMinutes() != null ? companyUser.getTrackingBalanceInMinutes() : 0;
            sb.append("- ").append(companyUser.getUsername())
                    .append(" | Resturlaub ").append(String.format(Locale.GERMAN, "%.1f", remainingVacation)).append(" Tage")
                    .append(" | Überstunden ").append(minutes)
                    .append(" Minuten")
                    .append(" | Jahresurlaub ").append(companyUser.getAnnualVacationDays() != null ? companyUser.getAnnualVacationDays() : 25)
                    .append("\n");
        }

        return sb.toString();
    }

    private boolean isAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleName()) || "ROLE_SUPERADMIN".equals(r.getRoleName()));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
    }
}
