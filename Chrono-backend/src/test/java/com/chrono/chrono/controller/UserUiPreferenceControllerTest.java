package com.chrono.chrono.controller;

import com.chrono.chrono.dto.UiPreferenceResponse;
import com.chrono.chrono.dto.UiPreferenceUpdateRequest;
import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.chrono.chrono.services.UserUiPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserUiPreferenceControllerTest {

    @Mock
    private UserUiPreferenceService preferenceService;

    private UserUiPreferenceController controller;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Principal principal = () -> "alice";

    @BeforeEach
    void setUp() {
        controller = new UserUiPreferenceController(preferenceService);
    }

    @Test
    void getReturnsRevisionZeroDefaultFromService() {
        UiPreferenceResponse response = new UiPreferenceResponse(
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                1,
                0,
                objectMapper.createObjectNode(),
                null
        );
        when(preferenceService.get(
                principal,
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                null
        )).thenReturn(response);

        ResponseEntity<UiPreferenceResponse> result = controller.get(
                "time_user_dashboard", "USER_STANDARD", null, principal);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
    }

    @Test
    void putUsesSelfScopedServiceContract() {
        UiPreferenceUpdateRequest request = new UiPreferenceUpdateRequest(
                1, 0L, objectMapper.createObjectNode());
        UiPreferenceResponse response = new UiPreferenceResponse(
                UserUiPreferenceArea.TIME_ADMIN_DASHBOARD,
                "ADMIN",
                1,
                0,
                request.payload(),
                null
        );
        when(preferenceService.put(
                principal,
                UserUiPreferenceArea.TIME_ADMIN_DASHBOARD,
                "ADMIN",
                null,
                request
        )).thenReturn(response);

        ResponseEntity<UiPreferenceResponse> result = controller.put(
                "TIME_ADMIN_DASHBOARD", "ADMIN", null, request, principal);

        assertSame(response, result.getBody());
    }

    @Test
    void deleteRequiresRevisionAndForwardsPmsPropertyContext() {
        ResponseEntity<Void> result = controller.delete(
                "PMS_DASHBOARD", null, 42L, 3L, principal);

        assertEquals(204, result.getStatusCode().value());
        verify(preferenceService).delete(
                principal,
                UserUiPreferenceArea.PMS_DASHBOARD,
                null,
                42L,
                3L
        );
    }

    @Test
    void rejectsUnknownAreaBeforeCallingService() {
        assertThrows(IllegalArgumentException.class, () -> controller.get(
                "NOT_A_REAL_AREA", null, null, principal));
    }
}
