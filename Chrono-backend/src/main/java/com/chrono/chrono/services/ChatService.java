package com.chrono.chrono.services;

import com.chrono.chrono.dto.ChatRequest;
import com.chrono.chrono.entities.CompanyKnowledge;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_HISTORY_ITEMS = 8;
    private static final int MAX_BASE_SNIPPETS = 5;
    private static final int MAX_COMPANY_SNIPPETS = 4;
    private static final int MAX_SNIPPET_LENGTH = 1400;
    private static final Set<String> STOP_WORDS = Set.of(
            "aber", "alle", "als", "auch", "auf", "aus", "bei", "bitte", "das", "dass", "dein",
            "deine", "dem", "den", "der", "des", "die", "du", "ein", "eine", "einer", "es", "fuer",
            "hat", "ich", "ihr", "ihre", "im", "in", "ist", "mein", "meine", "mit", "oder", "the",
            "und", "uns", "unser", "unsere", "von", "was", "welche", "wenn", "wer", "wie", "wir",
            "wo", "you", "your", "zur", "zum"
    );
    private static final Set<String> CHRONO_HINT_WORDS = Set.of(
            "chrono", "login", "profil", "profile", "urlaub", "ferien", "vacation", "zeiterfassung",
            "zeitkonto", "uberstunden", "ueberstunden", "overtime", "dashboard", "abrechnung", "payslip", "projekt",
            "projects", "task", "tasks", "mitarbeiter", "team", "admin", "firma", "company",
            "stempeln", "nfc", "abwesenheit", "absence"
    );
    private static final List<String> FALLBACKS = Arrays.asList(
            "Ich konnte dazu gerade keine verlaessliche Antwort erzeugen. Formuliere die Frage gerne konkreter.",
            "Dazu habe ich im Moment keine saubere Antwort. Mit etwas mehr Kontext kann ich dir besser helfen.",
            "Ich bin hier gerade unsicher. Frage mich am besten gezielter nach dem betroffenen Bereich in Chrono.",
            "Die Antwort ist im Moment nicht stabil genug. Ein genauerer Bezug auf den Workflow hilft mir."
    );

    @Value("${llm.base-url}")
    private String llmBaseUrl;

    @Value("${llama.model:llama3:8b}")
    private String modelName;

    private final RestTemplate restTemplate;
    private final RestTemplate longTimeoutRestTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CompanyKnowledgeService companyKnowledgeService;
    private final VacationService vacationService;
    private final UserRepository userRepository;
    private List<KnowledgeSnippet> knowledgeBaseSnippets = List.of();

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
        logger.info("Starte LLM-Warm-up im Hintergrund.");
        try {
            String response = askWithTemplate(longTimeoutRestTemplate, "Hallo, initialisiere dich bitte.", List.of(), null, -1);
            if (response != null && !response.contains("Fehler")) {
                logger.info("LLM-Warm-up erfolgreich abgeschlossen.");
            } else {
                logger.warn("LLM-Warm-up lieferte keine verwertbare Antwort.");
            }
        } catch (Exception e) {
            logger.error("Fehler waehrend des LLM-Warm-ups.", e);
        }
    }

    private void loadKnowledgeBaseFromResources() {
        logger.info("Lade Wissensdatenbank aus den Ressourcen.");
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:knowledge_base/*.md");
            this.knowledgeBaseSnippets = Arrays.stream(resources)
                    .sorted(Comparator.comparing(resource -> Optional.ofNullable(resource.getFilename()).orElse(""), String.CASE_INSENSITIVE_ORDER))
                    .map(this::toKnowledgeSnippet)
                    .filter(Objects::nonNull)
                    .toList();
            logger.info("Wissensdatenbank geladen: {} Dokumente.", knowledgeBaseSnippets.size());
        } catch (IOException e) {
            logger.error("Fehler beim Laden der Wissensdatenbank.", e);
        }
    }

    private KnowledgeSnippet toKnowledgeSnippet(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new KnowledgeSnippet(humanizeFileName(resource.getFilename()), trimForPrompt(content, MAX_SNIPPET_LENGTH), "Chrono Wissen");
        } catch (Exception e) {
            logger.error("Fehler beim Lesen der Wissensdatei {}", resource.getFilename(), e);
            return null;
        }
    }

    public String ask(String message, User user) {
        return ask(message, List.of(), user);
    }

    public String ask(String message, List<ChatRequest.ChatMessage> history, User user) {
        String sanitizedMessage = sanitizeUserText(message);
        if (sanitizedMessage.isBlank()) {
            return "Bitte stelle mir eine konkrete Frage.";
        }

        String selfAnswer = answerSelfServiceQuestion(sanitizedMessage, user);
        if (selfAnswer != null) {
            return selfAnswer;
        }

        if (user != null && isAdmin(user)) {
            String adminAnswer = answerAdminDataQuestion(sanitizedMessage, user);
            if (adminAnswer != null) {
                return adminAnswer;
            }
        }

        String quickLinkAnswer = answerQuickLinkQuestion(sanitizedMessage);
        if (quickLinkAnswer != null) {
            return quickLinkAnswer;
        }

        return askWithTemplate(restTemplate, sanitizedMessage, sanitizeHistory(history), user, 0);
    }

    private String answerSelfServiceQuestion(String message, User user) {
        if (user == null) {
            return null;
        }
        String normalized = normalize(message);
        boolean asksSelf = normalized.contains(" ich") || normalized.startsWith("ich ") || normalized.contains(" mein")
                || normalized.contains(" meine") || normalized.contains(" my ") || normalized.startsWith("my ");
        if (!asksSelf) {
            return null;
        }
        if (normalized.contains("uberstunden") || normalized.contains("ueberstunden") || normalized.contains("overtime")) {
            return "Du hast aktuell " + formatTrackingBalance(user.getTrackingBalanceInMinutes()) + " Stunden.";
        }
        if (normalized.contains("urlaub") || normalized.contains("ferien") || normalized.contains("vacation")) {
            int currentYear = LocalDate.now().getYear();
            double remaining = vacationService.calculateRemainingVacationDays(user.getUsername(), currentYear);
            int annual = user.getAnnualVacationDays() != null ? user.getAnnualVacationDays() : 25;
            return String.format(Locale.GERMAN, "Du hast fuer %d aktuell %.1f Tage Resturlaub (von %d).", currentYear, remaining, annual);
        }
        return null;
    }

    private String answerQuickLinkQuestion(String message) {
        String normalized = normalize(message);
        if (!normalized.contains("wo")) {
            return null;
        }
        Map<String, String> links = new LinkedHashMap<>();
        links.put("login", "/login");
        links.put("anmeldung", "/login");
        links.put("profil", "/profile");
        links.put("zeiterfassung", "/user");
        links.put("abrechnung", "/payslips");
        links.put("projekte", "/admin/projects");
        links.put("kunden", "/admin/customers");
        links.put("aufgaben", "/admin/tasks");
        for (Map.Entry<String, String> entry : links.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                String label = capitalize(entry.getKey());
                return "Den Bereich '" + label + "' erreichst du ueber [" + label + "](" + entry.getValue() + ") im Menue.";
            }
        }
        return null;
    }

    private String askWithTemplate(RestTemplate template, String message, List<ChatRequest.ChatMessage> history, User user, Number keepAliveDuration) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("prompt", buildPrompt(message, history, user));
            body.put("stream", false);
            body.put("options", buildGenerationOptions());
            if (keepAliveDuration.doubleValue() != 0) {
                body.put("keep_alive", keepAliveDuration);
            }

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            logger.info("Sende Chat-Anfrage an das LLM mit Modell {}.", modelName);
            String jsonResponse = template.postForObject(llmBaseUrl, requestEntity, String.class);
            logger.info("Antwort vom LLM erhalten.");
            if (jsonResponse == null || jsonResponse.isBlank()) {
                return getRandomFallback();
            }

            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            String answer = rootNode.path("response").asText("").trim();
            return answer.isBlank() ? getRandomFallback() : answer;
        } catch (Exception e) {
            logger.error("Fehler bei der Kommunikation mit dem LLM.", e);
            return getRandomFallback();
        }
    }

    private Map<String, Object> buildGenerationOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.2);
        options.put("top_p", 0.9);
        options.put("repeat_penalty", 1.08);
        options.put("num_predict", 600);
        return options;
    }

    private String buildPrompt(String message, List<ChatRequest.ChatMessage> history, User user) {
        StringBuilder prompt = new StringBuilder(5000);
        prompt.append("SYSTEM ANWEISUNG\n")
                .append("Du bist der Chrono-Assistent innerhalb des Systems Chrono.\n")
                .append("1. Antworte in der Sprache der aktuellen Nutzerfrage.\n")
                .append("2. Fuer Fragen zu Chrono, Workflows, Rollen, Urlaub, Zeiterfassung, Profilen oder Firmenprozessen nutze zuerst den bereitgestellten Kontext.\n")
                .append("3. Fuer allgemeine Wissensfragen ausserhalb von Chrono darfst du dein allgemeines Wissen nutzen, wenn der Kontext dazu nichts Spezifisches enthaelt.\n")
                .append("4. Erfinde niemals firmeninterne, personenbezogene oder rollenbeschraenkte Fakten. Wenn Informationen fehlen oder nicht freigegeben sind, sage das klar.\n")
                .append("5. Admin- oder Personaldaten duerfen nur beantwortet werden, wenn sie im freigegebenen Kontext enthalten sind.\n")
                .append("6. Wenn Live-Daten, News, Preise oder andere aktuelle Informationen fehlen, sage offen, dass du sie nicht live verifizieren kannst.\n")
                .append("7. Nutze den Gespraechsverlauf fuer Anschlussfragen.\n")
                .append("8. Antworte direkt, klar und knapp. Nutze kurze nummerierte Schritte nur wenn hilfreich.\n\n")
                .append("BENUTZERKONTEXT\n")
                .append(buildUserContext(user));

        String conversationContext = buildConversationContext(history);
        if (!conversationContext.isBlank()) {
            prompt.append("\nLETZTER GESPRAECHSVERLAUF\n").append(conversationContext).append("\n");
        }

        String baseKnowledge = buildRelevantKnowledgeContext(message, history);
        if (!baseKnowledge.isBlank()) {
            prompt.append("\nRELEVANTES CHRONO-WISSEN\n").append(baseKnowledge).append("\n");
        }

        String companyKnowledge = buildRelevantCompanyKnowledgeContext(user, message, history);
        if (!companyKnowledge.isBlank()) {
            prompt.append("\nRELEVANTES FIRMENWISSEN\n").append(companyKnowledge).append("\n");
        }

        String adminContext = buildAdminOperationalContext(user, message);
        if (!adminContext.isBlank()) {
            prompt.append("\nADMIN-OPERATIVER KONTEXT\n").append(adminContext).append("\n");
        }

        prompt.append("\nAKTUELLE FRAGE\n").append(message).append("\n")
                .append("\nANTWORTREGELN\n")
                .append("- Gib zuerst die direkte Antwort.\n")
                .append("- Wenn etwas unklar oder nicht verifizierbar ist, benenne das offen.\n");
        return prompt.toString();
    }

    private String buildUserContext(User user) {
        if (user == null) {
            return "- Nutzerstatus: Nicht eingeloggt oder Gast\n- Rollen: Keine Adminrechte\n- Zugriff: Kein Zugriff auf firmeninterne Admindaten\n";
        }
        String roles = user.getRoles() == null || user.getRoles().isEmpty()
                ? "ROLE_USER"
                : user.getRoles().stream().map(role -> role.getRoleName() == null ? "ROLE_USER" : role.getRoleName()).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
        String companyName = user.getCompany() != null && user.getCompany().getName() != null ? user.getCompany().getName() : "Keine Firma";
        return "- Benutzername: " + safeValue(user.getUsername(), "Unbekannt") + "\n- Rollen: " + roles + "\n- Firma: " + companyName + "\n- Adminzugriff: " + (isAdmin(user) ? "Ja" : "Nein") + "\n";
    }

    private String buildConversationContext(List<ChatRequest.ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatRequest.ChatMessage item : history) {
            String speaker = normalize(item.getSender());
            String label = speaker.contains("user") ? "Nutzer" : speaker.contains("assistant") || speaker.contains("bot") ? "Assistent" : "Nachricht";
            sb.append(label).append(": ").append(trimForPrompt(item.getText(), 320)).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildRelevantKnowledgeContext(String message, List<ChatRequest.ChatMessage> history) {
        return renderSnippetBlock(selectRelevantSnippets(knowledgeBaseSnippets, message, history, MAX_BASE_SNIPPETS, true));
    }

    private String buildRelevantCompanyKnowledgeContext(User user, String message, List<ChatRequest.ChatMessage> history) {
        if (user == null || user.getCompany() == null) {
            return "";
        }
        boolean admin = isAdmin(user);
        List<KnowledgeSnippet> companySnippets = Optional.ofNullable(companyKnowledgeService.findByCompany(user.getCompany())).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .filter(doc -> doc.getAccessLevel() == CompanyKnowledge.AccessLevel.ALL || admin)
                .sorted(Comparator.comparing(doc -> safeValue(doc.getTitle(), ""), String.CASE_INSENSITIVE_ORDER))
                .map(doc -> new KnowledgeSnippet(safeValue(doc.getTitle(), "Firmenwissen"), trimForPrompt(doc.getContent(), MAX_SNIPPET_LENGTH), "Firmenwissen"))
                .toList();
        return renderSnippetBlock(selectRelevantSnippets(companySnippets, message, history, MAX_COMPANY_SNIPPETS, false));
    }

    private List<KnowledgeSnippet> selectRelevantSnippets(List<KnowledgeSnippet> snippets, String message, List<ChatRequest.ChatMessage> history, int limit, boolean allowDefaultFallback) {
        if (snippets == null || snippets.isEmpty()) {
            return List.of();
        }
        String normalizedMessage = normalize(message);
        Set<String> keywords = extractKeywords(buildSearchText(message, history));
        List<Map.Entry<KnowledgeSnippet, Integer>> scored = new ArrayList<>();
        for (KnowledgeSnippet snippet : snippets) {
            int score = scoreSnippet(snippet, normalizedMessage, keywords);
            if (score > 0) {
                scored.add(Map.entry(snippet, score));
            }
        }
        scored.sort((left, right) -> {
            int byScore = Integer.compare(right.getValue(), left.getValue());
            return byScore != 0 ? byScore : String.CASE_INSENSITIVE_ORDER.compare(left.getKey().title(), right.getKey().title());
        });
        if (!scored.isEmpty()) {
            return scored.stream().limit(limit).map(Map.Entry::getKey).toList();
        }
        if (allowDefaultFallback && looksLikeChronoQuestion(normalizedMessage)) {
            return snippets.stream().limit(Math.min(2, limit)).toList();
        }
        return List.of();
    }

    private int scoreSnippet(KnowledgeSnippet snippet, String normalizedMessage, Set<String> keywords) {
        String normalizedTitle = normalize(snippet.title());
        String normalizedContent = normalize(snippet.content());
        int score = 0;
        for (String keyword : keywords) {
            if (normalizedTitle.contains(keyword)) {
                score += 5;
            }
            if (normalizedContent.contains(keyword)) {
                score += 2;
            }
        }
        if (!normalizedMessage.isBlank() && normalizedContent.contains(normalizedMessage)) {
            score += 6;
        }
        return score;
    }

    private String renderSnippetBlock(List<KnowledgeSnippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeSnippet snippet : snippets) {
            sb.append("- ").append(snippet.source()).append(": ").append(snippet.title()).append("\n").append(snippet.content()).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildSearchText(String message, List<ChatRequest.ChatMessage> history) {
        StringBuilder sb = new StringBuilder(message == null ? "" : message);
        for (ChatRequest.ChatMessage item : sanitizeHistory(history)) {
            sb.append('\n').append(item.getText());
        }
        return sb.toString();
    }

    private Set<String> extractKeywords(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return Set.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    private boolean looksLikeChronoQuestion(String normalizedMessage) {
        for (String hint : CHRONO_HINT_WORDS) {
            if (normalizedMessage.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private String answerAdminDataQuestion(String message, User requester) {
        if (message == null || requester.getCompany() == null) {
            return null;
        }
        String normalized = normalize(message);
        boolean asksVacation = normalized.contains("urlaub") || normalized.contains("vacation") || normalized.contains("ferientag") || normalized.contains("ferien");
        boolean asksOvertime = normalized.contains("uberstunden") || normalized.contains("ueberstunden") || normalized.contains("overtime");
        if (!asksVacation && !asksOvertime) {
            return null;
        }
        List<User> companyUsers = getCompanyUsersForAdmin(requester);
        if (companyUsers.isEmpty()) {
            return "Ich konnte keine Mitarbeitenden fuer deine Firma finden.";
        }
        Optional<User> explicitTarget = findMentionedUser(companyUsers, normalized);
        if (explicitTarget.isPresent()) {
            return buildDetailedUserSummary(explicitTarget.get(), asksVacation, asksOvertime);
        }
        return asksVacation ? buildCompanyVacationOverview(companyUsers) : buildCompanyOvertimeOverview(companyUsers);
    }

    private List<User> getCompanyUsersForAdmin(User admin) {
        if (admin.getCompany() == null || admin.getCompany().getId() == null) {
            return List.of();
        }
        List<User> users = Optional.ofNullable(userRepository.findByCompany_IdAndDeletedFalse(admin.getCompany().getId())).orElse(List.of());
        return users.isEmpty() ? Optional.ofNullable(userRepository.findByCompany_Id(admin.getCompany().getId())).orElse(List.of()) : users;
    }

    private Optional<User> findMentionedUser(List<User> users, String normalizedMessage) {
        return users.stream().filter(Objects::nonNull).filter(user -> {
            String username = normalize(user.getUsername());
            return !username.isBlank() && normalizedMessage.contains(username);
        }).findFirst();
    }

    private String buildDetailedUserSummary(User target, boolean includeVacation, boolean includeOvertime) {
        int currentYear = LocalDate.now().getYear();
        StringBuilder sb = new StringBuilder("Fuer '").append(target.getUsername()).append("': ");
        if (includeVacation) {
            double remaining = vacationService.calculateRemainingVacationDays(target.getUsername(), currentYear);
            int annual = target.getAnnualVacationDays() != null ? target.getAnnualVacationDays() : 25;
            sb.append(String.format(Locale.GERMAN, "Resturlaub %d: %.1f Tage (von %d). ", currentYear, remaining, annual));
        }
        if (includeOvertime) {
            sb.append("Ueberstunden-Saldo: ").append(formatTrackingBalance(target.getTrackingBalanceInMinutes())).append(" Stunden.");
        }
        return sb.toString().trim();
    }

    private String buildCompanyVacationOverview(List<User> users) {
        int currentYear = LocalDate.now().getYear();
        List<User> sorted = users.stream().filter(Objects::nonNull).sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER)).limit(20).toList();
        String summary = sorted.stream().map(user -> String.format(Locale.GERMAN, "%s: %.1f Tage", user.getUsername(), vacationService.calculateRemainingVacationDays(user.getUsername(), currentYear))).collect(Collectors.joining(" | "));
        return "Hier ist der Resturlaub fuer " + currentYear + " (Top " + sorted.size() + " Mitarbeitende): " + summary + ". Tipp: Frage zum Beispiel 'Wie viel Urlaub hat USERNAME?' fuer Details.";
    }

    private String buildCompanyOvertimeOverview(List<User> users) {
        List<User> sorted = users.stream().filter(Objects::nonNull).sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER)).limit(20).toList();
        String summary = sorted.stream().map(user -> user.getUsername() + ": " + formatTrackingBalance(user.getTrackingBalanceInMinutes()) + " h").collect(Collectors.joining(" | "));
        return "Hier ist der Ueberstunden-Saldo (Top " + sorted.size() + " Mitarbeitende): " + summary + ". Tipp: Frage zum Beispiel 'Wie viele Ueberstunden hat USERNAME?' fuer Details.";
    }

    private String buildAdminOperationalContext(User user, String message) {
        if (user == null || !isAdmin(user) || user.getCompany() == null || message == null) {
            return "";
        }
        String normalized = normalize(message);
        boolean wantsOperationalInsight = normalized.contains("team") || normalized.contains("mitarbeiter") || normalized.contains("company")
                || normalized.contains("firma") || normalized.contains("urlaub") || normalized.contains("uberstunden")
                || normalized.contains("ueberstunden")
                || normalized.contains("overtime") || normalized.contains("status") || normalized.contains("wer") || normalized.contains("who");
        if (!wantsOperationalInsight) {
            return "";
        }
        List<User> users = getCompanyUsersForAdmin(user).stream().filter(Objects::nonNull).sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER)).limit(25).toList();
        if (users.isEmpty()) {
            return "";
        }
        int currentYear = LocalDate.now().getYear();
        StringBuilder sb = new StringBuilder("Firma: ").append(user.getCompany().getName() != null ? user.getCompany().getName() : "Unbekannt").append("\nMitarbeitende: ").append(users.size()).append("\n");
        for (User companyUser : users) {
            double remainingVacation = 0.0;
            try {
                remainingVacation = vacationService.calculateRemainingVacationDays(companyUser.getUsername(), currentYear);
            } catch (Exception e) {
                logger.debug("Konnte Resturlaub fuer {} nicht berechnen: {}", companyUser.getUsername(), e.getMessage());
            }
            int annualVacation = companyUser.getAnnualVacationDays() != null ? companyUser.getAnnualVacationDays() : 25;
            sb.append("- ").append(companyUser.getUsername()).append(" | Resturlaub ").append(String.format(Locale.GERMAN, "%.1f", remainingVacation)).append(" Tage | Ueberstunden ").append(formatTrackingBalance(companyUser.getTrackingBalanceInMinutes())).append(" h | Jahresurlaub ").append(annualVacation).append("\n");
        }
        return sb.toString().trim();
    }

    private List<ChatRequest.ChatMessage> sanitizeHistory(List<ChatRequest.ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatRequest.ChatMessage> cleaned = new ArrayList<>();
        for (ChatRequest.ChatMessage item : history) {
            if (item == null) {
                continue;
            }
            String text = trimForPrompt(sanitizeUserText(item.getText()), 320);
            if (!text.isBlank()) {
                cleaned.add(new ChatRequest.ChatMessage(sanitizeUserText(item.getSender()), text));
            }
        }
        if (cleaned.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, cleaned.size() - MAX_HISTORY_ITEMS);
        return List.copyOf(cleaned.subList(fromIndex, cleaned.size()));
    }

    private String formatTrackingBalance(Integer minutesValue) {
        int minutes = minutesValue != null ? minutesValue : 0;
        int hours = Math.abs(minutes) / 60;
        int mins = Math.abs(minutes) % 60;
        String sign = minutes < 0 ? "-" : "";
        return String.format(Locale.ROOT, "%s%d:%02d", sign, hours, mins);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace("ß", "ss")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String sanitizeUserText(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private String trimForPrompt(String value, int maxLength) {
        String cleaned = value == null ? "" : value.replace("\r", "").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private String humanizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Chrono Wissen";
        }
        String withoutExtension = fileName.replaceFirst("\\.[^.]+$", "");
        return Arrays.stream(withoutExtension.split("[_-]")).filter(part -> !part.isBlank()).map(this::capitalize).collect(Collectors.joining(" "));
    }

    private String capitalize(String value) {
        return value == null || value.isBlank() ? "" : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String safeValue(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private boolean isAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getRoleName()) || "ROLE_SUPERADMIN".equals(role.getRoleName()));
    }

    private String getRandomFallback() {
        return FALLBACKS.get(ThreadLocalRandom.current().nextInt(FALLBACKS.size()));
    }

    private record KnowledgeSnippet(String title, String content, String source) {}
}
