package com.chrono.chrono.controller;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NfcHardwareIsolationTest {
    @Test
    void remoteAgentCannotReadLocalHardwareAndLocalAgentCannotProgramForeignUsername() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CompanyRepository companies = mock(CompanyRepository.class);
        when(companies.existsById(anyLong())).thenReturn(true);
        MockEnvironment env = new MockEnvironment().withProperty("LOCAL_SECRET", "local-token").withProperty("REMOTE_SECRET", "remote-token");
        NfcAgentAuthService agents = new NfcAgentAuthService(mapper, env, companies);
        ReflectionTestUtils.setField(agents, "registryJson", """
                [{"companyId":17,"deviceId":"local","tokenEnv":"LOCAL_SECRET","localHardware":true},
                 {"companyId":29,"deviceId":"remote","tokenEnv":"REMOTE_SECRET"}]
                """);
        AccessControlService access = mock(AccessControlService.class);
        NfcService hardware = mock(NfcService.class);
        when(hardware.readBlock(1)).thenReturn("00000000000000000000000000000000");
        NfcController controller = new NfcController(hardware, agents, access);
        assertThrows(AccessDeniedException.class, () -> controller.readNfc(1, "remote-token", null, null));
        verify(hardware, never()).readBlock(anyInt());
        assertEquals(200, controller.readNfc(1, "local-token", null, null).getStatusCode().value());

        Company other = new Company("Other"); other.setId(29L);
        User foreign = new User(); foreign.setUsername("foreign-user"); foreign.setCompany(other);
        when(access.requireTargetUser("foreign-user")).thenReturn(foreign);
        var foreignRequest = mapper.readValue("{\"data\":\"" + encoded("foreign-user") + "\"}", NfcController.WriteSectorRequest.class);
        assertThrows(AccessDeniedException.class, () -> controller.writeSector0("local-token", null, foreignRequest, null));
        verify(hardware, never()).writeSector0Block1(anyString());

        Company praxis = new Company("Praxis"); praxis.setId(17L);
        User owned = new User(); owned.setUsername("owned-user"); owned.setCompany(praxis);
        when(access.requireTargetUser("owned-user")).thenReturn(owned);
        when(hardware.writeSector0Block1(encoded("owned-user"))).thenReturn("written");
        var ownedRequest = mapper.readValue("{\"data\":\"" + encoded("owned-user") + "\"}", NfcController.WriteSectorRequest.class);
        assertEquals(200, controller.writeSector0("local-token", null, ownedRequest, null).getStatusCode().value());
        verify(hardware).writeSector0Block1(encoded("owned-user"));

        User admin = new User(); admin.setCompany(praxis); admin.setRoles(Set.of(new Role("ROLE_ADMIN")));
        when(access.requireAuthenticatedUser(any())).thenReturn(admin);
        Principal principal = () -> "admin";
        for (int alias : new int[] {257, -255, -1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            var request = mapper.readValue("{\"block\":" + alias + ",\"data\":\"" + encoded("foreign-user") + "\"}", NfcController.WriteRequest.class);
            var error = assertThrows(ResponseStatusException.class,
                    () -> controller.writeNfc(request, "local-token", null, null, principal));
            assertEquals(400, error.getStatusCode().value());
            var readError = assertThrows(ResponseStatusException.class,
                    () -> controller.readNfc(alias, "local-token", null, null));
            assertEquals(400, readError.getStatusCode().value());
        }
        verify(hardware, never()).writeBlock(anyInt(), anyString());

        var foreignBlockOne = mapper.readValue("{\"block\":1,\"data\":\"" + encoded("foreign-user") + "\"}", NfcController.WriteRequest.class);
        assertThrows(AccessDeniedException.class,
                () -> controller.writeNfc(foreignBlockOne, "local-token", null, null, principal));
        verify(hardware, never()).writeBlock(anyInt(), anyString());

        when(hardware.writeBlock(anyInt(), anyString())).thenReturn("written");
        var ownedBlockOne = mapper.readValue("{\"block\":1,\"data\":\"" + encoded("owned-user") + "\"}", NfcController.WriteRequest.class);
        assertEquals(200, controller.writeNfc(ownedBlockOne, "local-token", null, null, principal).getStatusCode().value());
        verify(hardware).writeBlock(1, encoded("owned-user"));
        for (int boundary : new int[] {0, 255}) {
            var request = mapper.readValue("{\"block\":" + boundary + ",\"data\":\"" + encoded("foreign-user") + "\"}", NfcController.WriteRequest.class);
            assertEquals(200, controller.writeNfc(request, "local-token", null, null, principal).getStatusCode().value());
            verify(hardware).writeBlock(boundary, encoded("foreign-user"));
        }
    }

    private String encoded(String username) {
        return HexFormat.of().formatHex(Arrays.copyOf(username.getBytes(StandardCharsets.UTF_8), 16));
    }
}
