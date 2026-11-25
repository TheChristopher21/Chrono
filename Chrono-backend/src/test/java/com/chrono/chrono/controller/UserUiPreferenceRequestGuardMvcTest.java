package com.chrono.chrono.controller;

import com.chrono.chrono.config.JsonReadConstraintsConfig;
import com.chrono.chrono.config.UiPreferenceRequestSizeFilter;
import com.chrono.chrono.dto.UiPreferenceResponse;
import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.chrono.chrono.exceptions.GlobalExceptionHandler;
import com.chrono.chrono.services.UserUiPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserUiPreferenceRequestGuardMvcTest {

    @Mock
    private UserUiPreferenceService preferenceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JsonReadConstraintsConfig().jsonStreamReadConstraintsCustomizer().customize(builder);
        ObjectMapper constrainedMapper = builder.build();

        mockMvc = MockMvcBuilders.standaloneSetup(new UserUiPreferenceController(preferenceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(constrainedMapper))
                .addFilters(new UiPreferenceRequestSizeFilter())
                .build();
    }

    @Test
    void oversizedPreferenceBodyIsRejectedBeforeJsonBindingAndService() throws Exception {
        String body = "{\"schemaVersion\":1,\"revision\":0,\"payload\":{\"padding\":\""
                + "x".repeat(70_000)
                + "\"}}";

        mockMvc.perform(put("/api/ui/preferences/APP_TABS")
                        .param("context", "workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("UI preference request body is too large."));

        verifyNoInteractions(preferenceService);
    }

    @Test
    void excessivelyDeepJsonIsRejectedByJacksonBeforeService() throws Exception {
        StringBuilder body = new StringBuilder("{\"schemaVersion\":1,\"revision\":0,\"payload\":");
        for (int i = 0; i < 70; i++) {
            body.append("{\"child\":");
        }
        body.append("null");
        body.append("}".repeat(70)).append("}");

        mockMvc.perform(put("/api/ui/preferences/TIME_USER_DASHBOARD")
                        .param("context", "USER_STANDARD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Der Request-Body ist ungültig."));

        verifyNoInteractions(preferenceService);
    }

    @Test
    void boundedBodyIsReplayedToMvcAndReachesSelfScopedService() throws Exception {
        String body = "{\"schemaVersion\":1,\"revision\":0,\"payload\":{\"tabs\":[]}}";
        UiPreferenceResponse response = new UiPreferenceResponse(
                UserUiPreferenceArea.APP_TABS,
                "workspace",
                1,
                0,
                new ObjectMapper().readTree("{\"tabs\":[]}"),
                null
        );
        when(preferenceService.put(
                any(Principal.class),
                org.mockito.ArgumentMatchers.eq(UserUiPreferenceArea.APP_TABS),
                org.mockito.ArgumentMatchers.eq("workspace"),
                org.mockito.ArgumentMatchers.isNull(),
                any()
        )).thenReturn(response);

        mockMvc.perform(put("/api/ui/preferences/APP_TABS")
                        .principal(() -> "alice")
                        .param("context", "workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context").value("workspace"))
                .andExpect(jsonPath("$.payload.tabs").isArray());

        verify(preferenceService).put(
                any(Principal.class),
                org.mockito.ArgumentMatchers.eq(UserUiPreferenceArea.APP_TABS),
                org.mockito.ArgumentMatchers.eq("workspace"),
                org.mockito.ArgumentMatchers.isNull(),
                any()
        );
    }
}
