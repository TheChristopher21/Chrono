package com.chrono.chrono.controller;

import com.chrono.chrono.dto.NfcCommandRequest;
import com.chrono.chrono.entities.NfcCommand;
import com.chrono.chrono.services.NfcAgentAuthService;
import com.chrono.chrono.services.NfcCommandService;
import com.chrono.chrono.services.AccessControlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.security.Principal;

@RestController
@RequestMapping("/api/nfc")
public class NfcCommandController {

    @Autowired
    private NfcCommandService nfcCommandService;

    @Autowired
    private NfcAgentAuthService nfcAgentAuthService;

    @Autowired
    private AccessControlService accessControlService;

    @GetMapping("/command/status/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<?> getCommandStatus(@PathVariable Long id, Principal principal) {
        Optional<NfcCommand> commandOpt = nfcCommandService.getCommandById(accessControlService.requireAuthenticatedUser(principal), id);
        if (commandOpt.isPresent()) {
            return ResponseEntity.ok(commandOpt.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Command not found");
        }
    }


    @PostMapping("/command")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<?> createCommand(@RequestBody NfcCommandRequest request, Principal principal) {
        NfcCommand command = nfcCommandService.createCommand(accessControlService.requireAuthenticatedUser(principal), request.getType(), request.getData());
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
        NfcAgentAuthService.AgentIdentity agent = nfcAgentAuthService.requireAgent(token, legacyHeader, httpRequest);
        return nfcCommandService.getPendingCommand(agent).map(command -> {
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

        NfcAgentAuthService.AgentIdentity agent = nfcAgentAuthService.requireAgent(token, legacyHeader, httpRequest);

        if (!status.equalsIgnoreCase("done")) {
            return ResponseEntity.badRequest().build();
        }
        NfcCommand command = nfcCommandService.markCommandDone(agent, id);
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("id", command.getId());
        responseMap.put("status", command.getStatus());
        responseMap.put("message", "Befehl wurde als erledigt markiert");
        responseMap.put("type", command.getType());
        responseMap.put("data", command.getData());
        return ResponseEntity.ok(responseMap);
    }
}
