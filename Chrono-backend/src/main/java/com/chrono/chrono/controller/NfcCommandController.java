package com.chrono.chrono.controller;

import com.chrono.chrono.dto.NfcCommandRequest;
import com.chrono.chrono.entities.NfcCommand;
import com.chrono.chrono.services.NfcAgentAuthService;
import com.chrono.chrono.services.NfcCommandService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/nfc")
public class NfcCommandController {

    @Autowired
    private NfcCommandService nfcCommandService;

    @Autowired
    private NfcAgentAuthService nfcAgentAuthService;

    @GetMapping("/command/status/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<?> getCommandStatus(@PathVariable Long id) {
        Optional<NfcCommand> commandOpt = nfcCommandService.getCommandById(id);
        if (commandOpt.isPresent()) {
            return ResponseEntity.ok(commandOpt.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Command not found");
        }
    }


    @PostMapping("/command")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<?> createCommand(@RequestBody NfcCommandRequest request) {
        NfcCommand command = nfcCommandService.createCommand(request.getType(), request.getData());
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("id", command.getId());
        responseMap.put("status", "success");
        responseMap.put("message", "Befehl wurde erfolgreich erstellt");
        responseMap.put("type", command.getType());
        responseMap.put("data", command.getData());
        return ResponseEntity.ok(responseMap);
    }

    @GetMapping("/command")
    public ResponseEntity<?> getPendingCommand(
            @RequestHeader(value = "X-Agent-Token", required = false) String token,
            @RequestHeader(value = "X-NFC-Agent-Request", required = false) String legacyHeader,
            HttpServletRequest httpRequest) {
        nfcAgentAuthService.requireAgent(token, legacyHeader, httpRequest);
        return nfcCommandService.getPendingCommand().map(command -> {
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("id", command.getId());
            responseMap.put("status", command.getStatus());
            responseMap.put("message", "Pending command retrieved successfully");
            responseMap.put("type", command.getType());
            responseMap.put("data", command.getData());
            return ResponseEntity.ok(responseMap);
        }).orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Update-Endpoint: Erwartet den Header "X-Agent-Token"
    @PutMapping("/command/{id}")
    public ResponseEntity<?> updateCommandStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestHeader(value = "X-Agent-Token", required = false) String token,
            @RequestHeader(value = "X-NFC-Agent-Request", required = false) String legacyHeader,
            HttpServletRequest httpRequest) {

        // Vergleiche mit trim() (entfernt zufällige Leerzeichen)
        if (!nfcAgentAuthService.isAgentRequest(token, legacyHeader, httpRequest)) {
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("status", "error");
            responseMap.put("message", "Unauthorized: Invalid or missing agent token");
            return ResponseEntity.status(403).body(responseMap);
        }

        if (!status.equalsIgnoreCase("done")) {
            return ResponseEntity.badRequest().build();
        }
        NfcCommand command = nfcCommandService.markCommandDone(id);
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("id", command.getId());
        responseMap.put("status", command.getStatus());
        responseMap.put("message", "Befehl wurde als erledigt markiert");
        responseMap.put("type", command.getType());
        responseMap.put("data", command.getData());
        return ResponseEntity.ok(responseMap);
    }
}
