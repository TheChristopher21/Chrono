package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsHousekeepingDtos;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class PmsHousekeepingCommandService {
    private final PmsPropertyAccessService access; private final HotelPropertyRepository properties;
    private final PmsHousekeepingService housekeeping; private final EntityManager em; private final ObjectMapper mapper;
    public record Command(@NotNull UUID commandId, @NotNull @Positive Long taskId, @NotNull @Valid PmsHousekeepingDtos.Update update) {}
    @Transactional
    public PmsHousekeepingDtos.Task execute(String username, Long propertyId, Command input) {
        var actor = access.access(username); access.require(actor, propertyId, "HOUSEKEEPING", true);
        var property = properties.findByIdAndCompany_IdForUpdate(propertyId, actor.companyId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(input).getBytes(StandardCharsets.UTF_8)));
            var previous = em.createQuery("select c from PmsHousekeepingCommand c where c.property.id=:property and c.commandId=:command", PmsHousekeepingCommand.class)
                    .setParameter("property", propertyId).setParameter("command", input.commandId().toString()).getResultStream().findFirst();
            if (previous.isPresent()) {
                var value = previous.get();
                if (!value.getRequestHash().equals(hash) || !value.getActor().equals(username)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Diese Offline-Aktion wurde bereits mit anderen Angaben verwendet.");
                return mapper.readValue(value.getResultJson(), PmsHousekeepingDtos.Task.class);
            }
            var result = housekeeping.update(property.getCompany(), propertyId, input.taskId(), input.update());
            var command = new PmsHousekeepingCommand(); command.setProperty(property); command.setCommandId(input.commandId().toString());
            command.setActor(username); command.setRequestHash(hash); command.setResultJson(mapper.writeValueAsString(result)); command.setCreatedAt(LocalDateTime.now()); em.persist(command);
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("Housekeeping-Aktion konnte nicht gespeichert werden", failure); }
    }
}
