package com.chrono.chrono.controller;

import com.chrono.chrono.exceptions.NfcNoCardException;
import com.chrono.chrono.services.NfcAgentAuthService;
import com.chrono.chrono.services.NfcService;
import com.chrono.chrono.services.AccessControlService;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.security.CompanyScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.HexFormat;
import java.util.Objects;
import java.security.Principal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/nfc")
public class NfcController {

    private final NfcService nfcService;
    private final NfcAgentAuthService nfcAgentAuthService;
    private final AccessControlService accessControl;
    // Den Agent Token als Konstante – bei Bedarf auch über Properties injizieren.

    public NfcController(NfcService nfcService, NfcAgentAuthService nfcAgentAuthService, AccessControlService accessControl) {
        this.nfcService = nfcService;
        this.nfcAgentAuthService = nfcAgentAuthService;
        this.accessControl = accessControl;
    }

    @GetMapping("/read/{block}")
    public ResponseEntity<?> readNfc(@PathVariable int block,
                                     @RequestHeader(value = "X-Agent-Token", required = false) String token,
                                     @RequestHeader(value = "X-NFC-Agent-Request", required = false) String legacyHeader,
                                     HttpServletRequest httpRequest) {
        NfcAgentAuthService.AgentIdentity agent = nfcAgentAuthService.requireAgent(token, legacyHeader, httpRequest);
        nfcAgentAuthService.requireLocalHardware(agent);
        requireValidBlock(block);
        try {
            String data = nfcService.readBlock(block);
            Map<String, String> body = new HashMap<>();
            body.put("status", "success");
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (NfcNoCardException e) {
            Map<String, String> body = new HashMap<>();
            body.put("status", "no-card");
            body.put("message", e.getMessage());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("status", "error");
            errorBody.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorBody);
        }
    }

    @PostMapping("/write")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<?> writeNfc(@RequestBody WriteRequest request,
                                     @RequestHeader(value = "X-Agent-Token", required = false) String token,
                                     @RequestHeader(value = "X-NFC-Agent-Request", required = false) String legacyHeader,
                                     HttpServletRequest httpRequest, Principal principal) {
        NfcAgentAuthService.AgentIdentity agent = nfcAgentAuthService.requireAgent(token, legacyHeader, httpRequest);
        nfcAgentAuthService.requireLocalHardware(agent);
        User actor = accessControl.requireAuthenticatedUser(principal);
        if (CompanyScope.companyFor(actor) == null || !Objects.equals(CompanyScope.companyFor(actor).getId(), agent.companyId())) {
            throw new AccessDeniedException("Select the NFC reader's company before writing");
        }
        requireValidBlock(request.getBlock());
        if (request.getBlock() == 1) requireProgrammingTarget(agent, request.getData());
        try {
            String result = nfcService.writeBlock(request.getBlock(), request.getData());
            Map<String, String> body = new HashMap<>();
            body.put("status", "success");
            body.put("message", result);
            return ResponseEntity.ok(body);
        } catch (NfcNoCardException e) {
            Map<String, String> body = new HashMap<>();
            body.put("status", "no-card");
            body.put("message", e.getMessage());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("status", "error");
            errorBody.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorBody);
        }
    }

    /**
     * Gesicherter Endpunkt zum Schreiben von Sektor 0 (Block 1) – für den Programmier-Modus.
     * Es wird überprüft, ob der Request über den richtigen "X-Agent-Token" verfügt.
     */
    @PostMapping("/write-sector0")
    public ResponseEntity<?> writeSector0(
            @RequestHeader(value = "X-Agent-Token", required = false) String token,
            @RequestHeader(value = "X-NFC-Agent-Request", required = false) String legacyHeader,
            @RequestBody WriteSectorRequest request,
            HttpServletRequest httpRequest) {

        Map<String, String> body = new HashMap<>();
        NfcAgentAuthService.AgentIdentity agent = nfcAgentAuthService.requireAgent(token, legacyHeader, httpRequest);
        nfcAgentAuthService.requireLocalHardware(agent);
        requireProgrammingTarget(agent, request.getData());

        try {
            String result = nfcService.writeSector0Block1(request.getData());
            body.put("status", "success");
            body.put("message", result);
            return ResponseEntity.ok(body);
        } catch (NfcNoCardException e) {
            body.put("status", "no-card");
            body.put("message", e.getMessage());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            body.put("status", "error");
            body.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    private void requireValidBlock(int block) {
        try {
            NfcService.validateBlockNumber(block);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private void requireProgrammingTarget(NfcAgentAuthService.AgentIdentity agent, String hexData) {
        String username;
        try {
            if (hexData == null || !hexData.matches("[0-9A-Fa-f]{32}")) throw new IllegalArgumentException();
            byte[] bytes = HexFormat.of().parseHex(hexData);
            int end = bytes.length;
            while (end > 0 && bytes[end - 1] == 0) end--;
            username = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, 0, end)).toString();
            if (username.isBlank() || username.indexOf('\0') >= 0) throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NFC data must encode a company username in 16 zero-padded UTF-8 bytes");
        }
        nfcAgentAuthService.requireUserInCompany(agent, accessControl.requireTargetUser(username));
    }

    public static class WriteRequest {
        private int block;
        private String data;

        public int getBlock() { return block; }
        public String getData() { return data; }
    }

    public static class WriteSectorRequest {
        private String data;

        public String getData() { return data; }
    }
}
