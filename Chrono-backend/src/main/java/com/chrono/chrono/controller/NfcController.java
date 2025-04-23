package com.chrono.chrono.controller;

import com.chrono.chrono.exceptions.NfcNoCardException;
import com.chrono.chrono.services.NfcService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/nfc")
public class NfcController {

    private final NfcService nfcService;
    // Den Agent Token als Konstante – bei Bedarf auch über Properties injizieren.
    private static final String AGENT_TOKEN = "SUPER-SECRET-AGENT-TOKEN";

    public NfcController(NfcService nfcService) {
        this.nfcService = nfcService;
    }

    @GetMapping("/read/{block}")
    public ResponseEntity<?> readNfc(@PathVariable int block) {
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
    public ResponseEntity<?> writeNfc(@RequestBody WriteRequest request) {
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
            @RequestBody WriteSectorRequest request) {

        Map<String, String> body = new HashMap<>();
        // Prüfe, ob ein Token übermittelt wurde und ob er korrekt ist.
        if (token == null || !AGENT_TOKEN.equals(token)) {
            body.put("status", "error");
            body.put("message", "Unauthorized: Invalid or missing agent token");
            return ResponseEntity.status(403).body(body);
        }

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

    private static class WriteRequest {
        private int block;
        private String data;

        public int getBlock() { return block; }
        public String getData() { return data; }
    }

    private static class WriteSectorRequest {
        private String data;

        public String getData() { return data; }
    }
}
