package com.chrono.chrono.services;

import com.chrono.chrono.entities.NfcCommand;
import com.chrono.chrono.repositories.NfcCommandRepository;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;


@Service
public class NfcCommandService {

    public Optional<NfcCommand> getCommandById(Long id) {
        return nfcCommandRepository.findById(id);
    }

    private static final Logger logger = LoggerFactory.getLogger(NfcCommandService.class);

    @Autowired
    private NfcCommandRepository nfcCommandRepository;

    // Erstelle einen neuen NFC-Befehl (z. B. "PROGRAM")
    public NfcCommand createCommand(String type, String data) {
        NfcCommand command = new NfcCommand();
        command.setType(type);
        command.setData(data);
        command.setStatus("pending");
        command.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return nfcCommandRepository.save(command);
    }

    // Hole den ältesten (nächsten) pending Befehl
    public Optional<NfcCommand> getPendingCommand() {
        Optional<NfcCommand> cmd = nfcCommandRepository.findFirstByStatusOrderByCreatedAtAsc("pending");

        return cmd;
    }


    // Aktualisiere den Befehl, z. B. setze den Status auf "done"
    public NfcCommand markCommandDone(Long id) {
        Optional<NfcCommand> commandOpt = nfcCommandRepository.findById(id);
        if (commandOpt.isPresent()) {
            NfcCommand cmd = commandOpt.get();
            cmd.setStatus("done");
            cmd.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
            return nfcCommandRepository.save(cmd);
        } else {
            throw new RuntimeException("Command not found");
        }
    }
}
