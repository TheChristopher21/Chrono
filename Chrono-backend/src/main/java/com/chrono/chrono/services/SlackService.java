package com.chrono.chrono.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class SlackService {

    @Value("${slack.webhook.url:}")
    private String webhookUrl;

    public void sendMessage(String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            String payload = "{\"text\":\"" + text.replace("\"", "\\\"") + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }
}
