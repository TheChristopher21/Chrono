package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.NfcCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NfcCommandServiceTest {
    private final NfcCommandRepository repository = mock(NfcCommandRepository.class);
    private final NfcAgentAuthService agents = mock(NfcAgentAuthService.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final List<NfcCommand> rows = new ArrayList<>();
    private final Company praxis = company(17L, "Praxis");
    private final Company other = company(29L, "Other");
    private final User admin = user("admin", praxis);
    private final User worker = user("worker", praxis);
    private final NfcAgentAuthService.AgentIdentity praxisReader = new NfcAgentAuthService.AgentIdentity(17L, "praxis-reader");
    private NfcCommandService service;

    @BeforeEach
    void setUp() {
        service = new NfcCommandService(repository, agents, access);
        when(access.isAdmin(any())).thenReturn(true);
        when(repository.save(any())).thenAnswer(call -> {
            NfcCommand command = call.getArgument(0);
            if (command.getId() == null) { command.setId(100L + rows.size()); rows.add(command); }
            return command;
        });
        when(repository.findByIdAndCompanyId(anyLong(), anyLong())).thenAnswer(call -> rows.stream()
                .filter(row -> Objects.equals(row.getId(), call.getArgument(0)) && Objects.equals(row.getCompanyId(), call.getArgument(1))).findFirst());
        when(repository.findByIdAndCompanyIdAndDeviceId(anyLong(), anyLong(), anyString())).thenAnswer(call -> rows.stream()
                .filter(row -> Objects.equals(row.getId(), call.getArgument(0)) && Objects.equals(row.getCompanyId(), call.getArgument(1))
                        && Objects.equals(row.getDeviceId(), call.getArgument(2))).findFirst());
        when(repository.findByCompanyIdAndDeviceIdAndStatusOrderByCreatedAtAscIdAsc(anyLong(), anyString(), anyString(), any(Pageable.class)))
                .thenAnswer(call -> rows.stream().filter(row -> Objects.equals(row.getCompanyId(), call.getArgument(0))
                        && Objects.equals(row.getDeviceId(), call.getArgument(1)) && Objects.equals(row.getStatus(), call.getArgument(2)))
                        .limit(((Pageable) call.getArgument(3)).getPageSize()).toList());
    }

    @Test
    void uiProgramUsernameUsesActorCompanyAndConfiguredDevice() {
        when(access.requireTargetUser("worker")).thenReturn(worker);
        when(agents.routeForCompany(17L)).thenReturn(praxisReader);
        NfcCommand command = service.createCommand(admin, "PROGRAM", "worker");
        assertEquals(17L, command.getCompanyId());
        assertEquals("praxis-reader", command.getDeviceId());
        assertEquals("worker", command.getData());
        assertEquals("PROGRAM", command.getType());
        assertEquals("pending", command.getStatus());
        assertEquals(worker.getId(), command.getTargetUserId());
    }

    @Test
    void programmingOtherCompanyUsernameFailsBeforeRoutingOrSaving() {
        when(access.requireTargetUser("other-worker")).thenReturn(user("other-worker", other));
        assertThrows(AccessDeniedException.class, () -> service.createCommand(admin, "PROGRAM", "other-worker"));
        verifyNoInteractions(agents);
        verify(repository, never()).save(any());
    }

    @Test
    void ownerlessLegacyOtherTenantAndOtherDeviceRowsStayOutOfPollAndCompletion() {
        NfcCommand legacy = add(null, null);
        NfcCommand foreign = add(29L, "other-reader");
        NfcCommand wrongDevice = add(17L, "other-device");
        NfcCommand owned = add(17L, "praxis-reader");
        owned.setTargetUserId(worker.getId()); owned.setData(worker.getUsername());
        when(access.requireTargetUser(worker.getId())).thenReturn(worker);
        assertSame(owned, service.getPendingCommand(praxisReader).orElseThrow());
        for (NfcCommand excluded : List.of(legacy, foreign, wrongDevice)) {
            assertThrows(ResponseStatusException.class, () -> service.markCommandDone(praxisReader, excluded.getId()));
            assertEquals("pending", excluded.getStatus());
            assertNull(excluded.getProcessedAt());
        }
        assertTrue(service.getCommandById(admin, foreign.getId()).isEmpty());
        assertTrue(service.getCommandById(admin, legacy.getId()).isEmpty());
        assertSame(owned, service.markCommandDone(praxisReader, owned.getId()));
        assertEquals("done", owned.getStatus());
        assertNotNull(owned.getProcessedAt());
        verify(repository, never()).findById(anyLong());
        verify(repository, never()).findAll();
    }

    @Test
    void unscopedAdministratorCannotReadOrEnqueueCommands() {
        User unscoped = user("superadmin", null);
        assertThrows(AccessDeniedException.class, () -> service.getCommandById(unscoped, 1L));
        assertThrows(AccessDeniedException.class, () -> service.createCommand(unscoped, "PROGRAM", "worker"));
        verifyNoInteractions(repository);
    }

    @Test
    void renamedTransferredMissingAndUnboundTargetsAreCancelledWithoutBlockingLaterValidWork() {
        User renamed = user("renamed-worker", praxis);
        User transferred = user("transferred-worker", other);
        NfcCommand renamedCommand = add(17L, "praxis-reader");
        renamedCommand.setTargetUserId(201L); renamedCommand.setData("original-worker");
        when(access.requireTargetUser(201L)).thenReturn(renamed);
        NfcCommand transferredCommand = add(17L, "praxis-reader");
        transferredCommand.setTargetUserId(202L); transferredCommand.setData(transferred.getUsername());
        when(access.requireTargetUser(202L)).thenReturn(transferred);
        NfcCommand missingCommand = add(17L, "praxis-reader");
        missingCommand.setTargetUserId(203L); missingCommand.setData("missing-worker");
        when(access.requireTargetUser(203L)).thenThrow(new AccessDeniedException("Target user not found"));
        NfcCommand unboundCommand = add(17L, "praxis-reader");
        NfcCommand legacy = add(null, null);
        NfcCommand valid = add(17L, "praxis-reader");
        valid.setTargetUserId(worker.getId()); valid.setData(worker.getUsername());
        when(access.requireTargetUser(worker.getId())).thenReturn(worker);

        assertSame(valid, service.getPendingCommand(praxisReader).orElseThrow());
        for (NfcCommand stale : List.of(renamedCommand, transferredCommand, missingCommand, unboundCommand)) {
            assertEquals("cancelled", stale.getStatus()); assertNotNull(stale.getProcessedAt());
        }
        assertEquals("original-worker", renamedCommand.getData());
        assertEquals("transferred-worker", transferredCommand.getData());
        assertEquals("missing-worker", missingCommand.getData());
        assertEquals("pending", legacy.getStatus()); assertNull(legacy.getProcessedAt());
        assertEquals("pending", valid.getStatus()); assertNull(valid.getProcessedAt());
        assertThrows(ResponseStatusException.class, () -> service.markCommandDone(praxisReader, renamedCommand.getId()));
        assertEquals("cancelled", renamedCommand.getStatus());
        verify(access, never()).requireTargetUser(anyString());
    }

    @Test
    void staleDispatchScanIsBoundedAndNextPollCanReachLaterValidCommand() {
        for (int i = 0; i < 30; i++) add(17L, "praxis-reader");
        NfcCommand valid = add(17L, "praxis-reader");
        valid.setTargetUserId(worker.getId()); valid.setData(worker.getUsername());
        when(access.requireTargetUser(worker.getId())).thenReturn(worker);
        assertTrue(service.getPendingCommand(praxisReader).isEmpty());
        assertEquals(25, rows.stream().filter(row -> "cancelled".equals(row.getStatus())).count());
        assertSame(valid, service.getPendingCommand(praxisReader).orElseThrow());
        assertEquals(30, rows.stream().filter(row -> "cancelled".equals(row.getStatus())).count());
        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, times(2)).findByCompanyIdAndDeviceIdAndStatusOrderByCreatedAtAscIdAsc(eq(17L), eq("praxis-reader"), eq("pending"), pages.capture());
        assertTrue(pages.getAllValues().stream().allMatch(page -> page.getPageSize() == 25 && page.getPageNumber() == 0));
    }

    private NfcCommand add(Long companyId, String device) {
        NfcCommand command = new NfcCommand();
        command.setId(1L + rows.size()); command.setCompanyId(companyId); command.setDeviceId(device);
        command.setType("PROGRAM"); command.setData("unchanged-legacy-data"); rows.add(command); return command;
    }

    private static Company company(Long id, String name) { Company company = new Company(name); company.setId(id); return company; }
    private static User user(String name, Company company) { User user = new User(); user.setId((long) Math.abs(name.hashCode())); user.setUsername(name); user.setCompany(company); return user; }
}
