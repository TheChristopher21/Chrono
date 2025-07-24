package com.chrono.chrono.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class ChatService {

    @Value("${llm.base-url:http://localhost:5000}")
    private String llmBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String ask(String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> req = new HttpEntity<>(Map.of("prompt", message), headers);
            Map<?, ?> resp = restTemplate.postForObject(llmBaseUrl, req, Map.class);
            if (resp != null && resp.get("response") != null) {
                return resp.get("response").toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
