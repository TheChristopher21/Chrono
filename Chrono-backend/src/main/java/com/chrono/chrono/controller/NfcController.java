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

    // Neuer Endpunkt für Sektor 0, Block 1 mit Schlüssel-Iteration
    @PostMapping("/write-sector0")
    public ResponseEntity<?> writeSector0(@RequestBody WriteSectorRequest request) {
        try {
            String result = nfcService.writeSector0Block1(request.getData());
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
