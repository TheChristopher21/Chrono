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
import com.chrono.chrono.services.CompanyKnowledgeService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
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
                       CompanyKnowledgeService companyKnowledgeService) {
        this.restTemplate = restTemplate;
        this.longTimeoutRestTemplate = longTimeoutRestTemplate;
        this.companyKnowledgeService = companyKnowledgeService;
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
            String fullPrompt = "Antworte auf die folgende Frage nur basierend auf dem untenstehenden Kontext. Antworte in der gleichen Sprache wie die Frage.\n\n" +
                    "Benutzerkontext: " + userContext + "\n\n" +
                    "--- KONTEXT ---\n" +
                    this.knowledgeBaseContent +
                    companyKnowledge +
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
}