package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ChatRequest;
import com.chrono.chrono.dto.ChatResponse;
import com.chrono.chrono.dto.ChatResult;
import com.chrono.chrono.dto.ChatStatusResponse;
import com.chrono.chrono.entities.ComplianceAuditLog;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.ChatFeatureService;
import com.chrono.chrono.services.ChatRateLimiter;
import com.chrono.chrono.services.ChatService;
import com.chrono.chrono.services.ComplianceAuditService;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserService userService;

    @Autowired
    private ChatFeatureService chatFeatureService;

    @Autowired
    private ChatRateLimiter chatRateLimiter;

    @Autowired
    private ComplianceAuditService complianceAuditService;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatStatusResponse> status(Principal principal) {
        User user = resolveUser(principal);
        boolean enabled = chatFeatureService.isChatbotEnabled(user);
        String message = enabled
                ? "Der Chat ist fuer diese Firma aktiviert. Aktionen werden nur als Vorschlag angezeigt und nicht automatisch ausgefuehrt."
                : "Der Chat ist fuer diese Firma nicht freigeschaltet.";
        return ResponseEntity.ok(new ChatStatusResponse(
                enabled,
                chatService.getModelName(),
                "ollama",
                chatRateLimiter.getMaxRequests(),
                chatRateLimiter.getWindowSeconds(),
                "keyword-rag-with-sources",
                "suggestions-only",
                message
        ));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req, Principal principal) {
        User user = resolveUser(principal);
        String message = req != null ? req.getMessage() : null;

        if (!chatFeatureService.isChatbotEnabled(user)) {
            ChatResponse response = new ChatResponse("Der KI-Chatbot ist fuer deine Firma nicht freigeschaltet.");
            response.setStatus("FEATURE_DISABLED");
            response.setSafetyLevel("DENIED");
            audit(user, "AI_CHAT_DENIED", message, "feature=chatbot; reason=disabled", ComplianceAuditLog.Severity.WARNING);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        ChatRateLimiter.RateLimitDecision limit = chatRateLimiter.check(user);
        if (!limit.allowed()) {
            ChatResponse response = new ChatResponse("Du hast das Anfrage-Limit fuer den KI-Chat erreicht. Bitte versuche es in ein paar Minuten erneut.");
            response.setStatus("RATE_LIMITED");
            response.setSafetyLevel("THROTTLED");
            response.setRemainingRequests(0);
            response.setRetryAfterSeconds(limit.retryAfterSeconds());
            audit(user, "AI_CHAT_RATE_LIMITED", message, "retryAfterSeconds=" + limit.retryAfterSeconds(), ComplianceAuditLog.Severity.WARNING);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(limit.retryAfterSeconds()))
                    .body(response);
        }

        List<ChatRequest.ChatMessage> history = req != null ? req.getHistory() : List.of();
        ChatResult result = chatService.askDetailed(message, history, user);
        ChatResponse response = new ChatResponse(result);
        response.setRemainingRequests(limit.remainingRequests());
        ComplianceAuditLog.Severity severity = "BLOCKED_PROMPT".equals(result.getStatus())
                ? ComplianceAuditLog.Severity.WARNING
                : ComplianceAuditLog.Severity.INFO;
        audit(user, "AI_CHAT_ASKED", message, "status=" + result.getStatus() + "; model=" + result.getModel() + "; latencyMs=" + result.getLatencyMs(), severity);
        return ResponseEntity.ok(response);
    }

    private User resolveUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.getUserByUsername(principal.getName());
    }

    private void audit(User user, String action, String message, String details, ComplianceAuditLog.Severity severity) {
        String preview = sanitizeAuditPreview(message);
        String safeDetails = (details == null ? "" : details) + "; preview=" + preview;
        complianceAuditService.recordAction(user, action, "AI_CHAT", null, safeDetails, severity);
    }

    private String sanitizeAuditPreview(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String cleaned = value
                .replaceAll("(?i)(bearer\\s+)[a-z0-9._\\-]+", "$1[redacted]")
                .replaceAll("(?i)(password|passwort|secret|token|api[_-]?key)\\s*[:=]\\s*\\S+", "$1=[redacted]")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 180) {
            cleaned = cleaned.substring(0, 177).trim() + "...";
        }
        return cleaned.toLowerCase(Locale.ROOT).contains("authorization:")
                ? "[redacted authorization header]"
                : cleaned;
    }
}
