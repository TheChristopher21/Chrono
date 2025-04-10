package com.chrono.chrono.controller;

import com.chrono.chrono.dto.NfcCommandRequest;
import com.chrono.chrono.entities.NfcCommand;
import com.chrono.chrono.services.NfcCommandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nfc")
public class NfcCommandController {

    @Autowired
    private NfcCommandService nfcCommandService;

    // Erstelle einen neuen NFC-Befehl (vom Admin-Panel aus)
    @PostMapping("/command")
    public ResponseEntity<NfcCommand> createCommand(@RequestBody NfcCommandRequest request) {
        NfcCommand command = nfcCommandService.createCommand(request.getType(), request.getData());
        return ResponseEntity.ok(command);
    }

    // Hole den nächsten pending Befehl (vom externen NFC-Agenten abgefragt)
    @GetMapping("/command")
    public ResponseEntity<?> getPendingCommand() {
        return nfcCommandService.getPendingCommand()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Aktualisiere den Befehl (z. B. setze Status auf "done" nach erfolgreicher Programmierung)
    @PutMapping("/command/{id}")
    public ResponseEntity<NfcCommand> updateCommandStatus(@PathVariable Long id, @RequestParam String status) {
        if (!status.equalsIgnoreCase("done")) {
            return ResponseEntity.badRequest().build();
        }
        NfcCommand command = nfcCommandService.markCommandDone(id);
        return ResponseEntity.ok(command);
    }
}
