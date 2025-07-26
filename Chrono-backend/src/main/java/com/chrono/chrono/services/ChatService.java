package com.chrono.chrono.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Value("${llm.base-url}")
    private String llmBaseUrl;

    @Value("${llama.model:tinyllama}")
    private String modelName;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String knowledgeBaseContent = "";

    public ChatService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(20))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        loadKnowledgeBase();
    }

    private void loadKnowledgeBase() {
        // Dieser Pfad entspricht dem, was wir in docker-compose.yml gemountet haben
        Path kbPath = Paths.get("/app/knowledge_base");
        logger.info("Versuche, Wissensdatenbank aus dem Verzeichnis zu laden: {}", kbPath);

        if (Files.isDirectory(kbPath)) {
            try (Stream<Path> paths = Files.walk(kbPath)) {
                this.knowledgeBaseContent = paths
                        .filter(Files::isRegularFile)
                        .map(path -> {
                            try {
                                return Files.readString(path);
                            } catch (IOException e) {
                                logger.error("Fehler beim Lesen der Datei: {}", path, e);
                                return "";
                            }
                        })
                        .collect(Collectors.joining("\n\n---\n\n"));
                logger.info("Wissensdatenbank erfolgreich geladen. Umfang: {} Zeichen.", this.knowledgeBaseContent.length());
            } catch (IOException e) {
                logger.error("Fehler beim Zugriff auf das Wissensdatenbank-Verzeichnis: {}", kbPath, e);
            }
        } else {
            logger.warn("Wissensdatenbank-Verzeichnis nicht gefunden unter: {}", kbPath);
        }
    }

    public String ask(String message) {
        logger.info("Neue Chat-Anfrage erhalten: '{}'", message);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Hier bauen wir den intelligenten Prompt zusammen
            String fullPrompt = "Antworte auf die folgende Frage nur basierend auf dem untenstehenden Kontext. Antworte auf Deutsch.\n\n" +
                    "--- KONTEXT ---\n" +
                    this.knowledgeBaseContent +
                    "\n--- FRAGE ---\n" +
                    message;

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("prompt", fullPrompt);
            body.put("stream", false);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            logger.info("Sende Anfrage an Ollama...");
            String jsonResponse = restTemplate.postForObject(llmBaseUrl, requestEntity, String.class);
            logger.info("Antwort von Ollama erhalten.");

            if (jsonResponse != null) {
                JsonNode rootNode = objectMapper.readTree(jsonResponse);
                if (rootNode.has("response")) {
                    String responseText = rootNode.get("response").asText();
                    logger.info("Erfolgreich Antwort extrahiert.");
                    return responseText;
                }
            }
        } catch (Exception e) {
            logger.error("Fehler bei der Kommunikation mit dem LLM:", e);
        }
        return "Entschuldigung, es gab einen Fehler bei der Kommunikation mit der KI.";
    }
}