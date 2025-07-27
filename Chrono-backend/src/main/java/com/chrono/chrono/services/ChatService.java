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

    @Value("${llama.model:llama3:8b}")
    private String modelName;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String knowledgeBaseContent = "";

    public ChatService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(20))
                // HIER IST DIE ÄNDERUNG: Erhöhtes Timeout auf 5 Minuten
                .setReadTimeout(Duration.ofMinutes(5))
                .build();
        loadKnowledgeBase();
    }

    private void loadKnowledgeBase() {
        Path kbPath = Paths.get("/app/knowledge_base");
        logger.info("Lade Wissensdatenbank aus: {}", kbPath);
        if (!Files.isDirectory(kbPath)) {
            logger.warn("Wissensdatenbank-Verzeichnis nicht gefunden!");
            return;
        }
        try (Stream<Path> paths = Files.walk(kbPath)) {
            this.knowledgeBaseContent = paths
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        try { return Files.readString(path); }
                        catch (IOException e) { return ""; }
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));
            logger.info("Wissensdatenbank geladen ({} Zeichen).", this.knowledgeBaseContent.length());
        } catch (IOException e) {
            logger.error("Fehler beim Lesen des Wissensdatenbank-Verzeichnisses.", e);
        }
    }

    public String ask(String message) {
        logger.info("Neue Chat-Anfrage: '{}'", message);

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

            if (jsonResponse != null) {
                String lastJsonLine = null;
                for (String line : jsonResponse.trim().split("\n")) {
                    if (!line.isEmpty()) {
                        lastJsonLine = line;
                    }
                }

                if (lastJsonLine != null) {
                    JsonNode rootNode = objectMapper.readTree(lastJsonLine);
                    if (rootNode.has("response")) {
                        String responseText = rootNode.get("response").asText();
                        logger.info("Erfolgreich Antwort extrahiert.");
                        return responseText;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fehler bei der Kommunikation mit dem LLM:", e);
        }
        return "Entschuldigung, es gab einen Fehler.";
    }
}