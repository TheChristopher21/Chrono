package com.chrono.chrono.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.chrono.chrono.entities.User;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Value("${llm.base-url}")
    private String llmBaseUrl;

    @Value("${llama.model:llama3:8b}")
    private String modelName;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String knowledgeBaseContent = "";

    public ChatService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(20))
                .setReadTimeout(Duration.ofMinutes(5))
                .build();
        loadKnowledgeBaseFromResources();
    }

    private void loadKnowledgeBaseFromResources() {
        // Liest jetzt aus dem 'resources/knowledge_base' Ordner innerhalb der Anwendung
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

    public String ask(String message, User user) {
        logger.info("Neue Chat-Anfrage: '{}'", message);

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

            String fullPrompt = "Antworte auf die folgende Frage nur basierend auf dem untenstehenden Kontext. Antworte in der gleichen Sprache wie die Frage.\n\n" +
                    "--- KONTEXT ---\n" +
                    this.knowledgeBaseContent +
                    "\n--- FRAGE ---\n" +
                    message;

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("prompt", fullPrompt);
            body.put("stream", false);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            logger.info("Sende Anfrage an Ollama mit Modell {}...", modelName);
            String jsonResponse = restTemplate.postForObject(llmBaseUrl, requestEntity, String.class);
            logger.info("Antwort von Ollama erhalten.");

            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            if (rootNode.has("response")) {
                return rootNode.get("response").asText();
            }

        } catch (Exception e) {
            logger.error("Fehler bei der Kommunikation mit dem LLM:", e);
        }
        return "Entschuldigung, es gab einen Fehler.";
    }
}