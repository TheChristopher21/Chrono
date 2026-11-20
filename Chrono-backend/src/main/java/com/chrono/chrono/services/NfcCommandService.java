package com.chrono.chrono.services;

import com.chrono.chrono.entities.NfcCommand;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.NfcCommandRepository;
import com.chrono.chrono.security.CompanyScope;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Objects;


@Service
public class NfcCommandService {

    private final NfcCommandRepository nfcCommandRepository;
    private final NfcAgentAuthService agents;
    private final AccessControlService accessControl;
    private static final int DISPATCH_BATCH_SIZE = 25;

    public NfcCommandService(NfcCommandRepository nfcCommandRepository, NfcAgentAuthService agents, AccessControlService accessControl) {
        this.nfcCommandRepository = nfcCommandRepository;
        this.agents = agents;
        this.accessControl = accessControl;
    }

    public Optional<NfcCommand> getCommandById(User actor, Long id) {
        return nfcCommandRepository.findByIdAndCompanyId(id, requireCompany(actor));
    }

    // Erstelle einen neuen NFC-Befehl (z. B. "PROGRAM")
    @Transactional
    public NfcCommand createCommand(User actor, String type, String data) {
        Long companyId = requireCompany(actor);
        if (!"PROGRAM".equalsIgnoreCase(type) || data == null || data.isBlank() || data.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PROGRAM and a company username are required");
        }
        User target = accessControl.requireTargetUser(data);
        if (target.getId() == null || target.getCompany() == null || !Objects.equals(companyId, target.getCompany().getId())) {
            throw new AccessDeniedException("NFC programming target must belong to your company");
        }
        NfcAgentAuthService.AgentIdentity route = agents.routeForCompany(companyId);
        NfcCommand command = new NfcCommand();
        command.setCompanyId(companyId);
        command.setTargetUserId(target.getId());
        command.setDeviceId(route.deviceId());
        command.setType("PROGRAM");
        command.setData(target.getUsername());
        command.setStatus("pending");
        command.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return nfcCommandRepository.save(command);
    }

    // Hole den ältesten (nächsten) pending Befehl
    @Transactional
    public Optional<NfcCommand> getPendingCommand(NfcAgentAuthService.AgentIdentity agent) {
        requireIdentity(agent);
        var candidates = nfcCommandRepository.findByCompanyIdAndDeviceIdAndStatusOrderByCreatedAtAscIdAsc(
                agent.companyId(), agent.deviceId(), "pending", PageRequest.of(0, DISPATCH_BATCH_SIZE));
        for (NfcCommand command : candidates) {
            if (currentProgrammingTargetMatches(command, agent)) return Optional.of(command);
            // Never replay stale intent or rewrite its raw username. Cancellation
            // removes only this owned command from future polls, then bounded
            // scanning continues so a stale head does not block valid work.
            command.setStatus("cancelled");
            command.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
            nfcCommandRepository.save(command);
        }
        return Optional.empty();
    }

    private boolean currentProgrammingTargetMatches(NfcCommand command, NfcAgentAuthService.AgentIdentity agent) {
        if (!"PROGRAM".equalsIgnoreCase(command.getType()) || command.getTargetUserId() == null) return false;
        try {
            User current = accessControl.requireTargetUser(command.getTargetUserId());
            return current != null && current.getCompany() != null
                    && Objects.equals(agent.companyId(), current.getCompany().getId())
                    && Objects.equals(command.getData(), current.getUsername());
        } catch (AccessDeniedException ex) {
            return false;
        }
    }


    // Aktualisiere den Befehl, z. B. setze den Status auf "done"
    @Transactional
    public NfcCommand markCommandDone(NfcAgentAuthService.AgentIdentity agent, Long id) {
        requireIdentity(agent);
        Optional<NfcCommand> commandOpt = nfcCommandRepository.findByIdAndCompanyIdAndDeviceId(id, agent.companyId(), agent.deviceId());
        if (commandOpt.isPresent()) {
            NfcCommand cmd = commandOpt.get();
            if ("cancelled".equals(cmd.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Command cancelled because its programming target changed");
            }
            cmd.setStatus("done");
            cmd.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
            return nfcCommandRepository.save(cmd);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Command not found");
        }
    }

    private Long requireCompany(User actor) {
        Company company = CompanyScope.companyFor(actor);
        if (actor == null || !accessControl.isAdmin(actor) || company == null || company.getId() == null) {
            throw new AccessDeniedException("Select a company before managing NFC commands");
        }
        return company.getId();
    }

    private void requireIdentity(NfcAgentAuthService.AgentIdentity identity) {
        if (identity == null || identity.companyId() == null || identity.deviceId() == null || identity.deviceId().isBlank()) {
            throw new AccessDeniedException("Configured NFC ownership required");
        }
    }
}
