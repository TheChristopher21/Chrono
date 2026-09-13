package com.chrono.chrono.controller.pms;

import com.chrono.chrono.services.pms.PmsLiveUpdateService;
import com.chrono.chrono.config.PmsAccessPolicy;
import jakarta.servlet.http.HttpServletRequest;
import com.chrono.chrono.utils.JwtUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.security.Principal;

@RestController
@RequestMapping("/api/pms/properties/{propertyId}/live")
public class PmsLiveUpdateController {
    private final PmsLiveUpdateService live; private final JwtUtil jwt;
    public PmsLiveUpdateController(PmsLiveUpdateService live, JwtUtil jwt) { this.live = live; this.jwt = jwt; }
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(@PathVariable Long propertyId, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, Principal principal, HttpServletRequest request) {
        if (principal == null || !authorization.startsWith("Bearer ")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        SseEmitter emitter = live.subscribe(principal.getName(), propertyId, jwt.extractExpiration(authorization.substring(7)).toInstant());
        request.setAttribute(PmsAccessPolicy.LIVE_AUTHORIZED, true);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> rejectedStream(ResponseStatusException error) {
        // SSE clients accept text/event-stream, so a JSON error body would fail content negotiation
        // and mask the original status with an unauthenticated error dispatch.
        var response = ResponseEntity.status(error.getStatusCode()).header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (error.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            response.header(HttpHeaders.RETRY_AFTER, "15");
        }
        return response.build();
    }
}
