package com.chrono.chrono.config;

import com.chrono.chrono.services.UserUiPreferenceService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "llm.warmup.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UiPreferenceSecurityFilterIntegrationTest {

    private static final String VALID_BODY =
            "{\"schemaVersion\":1,\"revision\":0,\"payload\":{\"tabs\":[]}}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    private UserUiPreferenceService preferenceService;

    @Test
    void preferenceSizeFilterRunsAfterAuthorizationFilter() {
        List<Filter> filters = springSecurityFilterChain.getFilters("/api/ui/preferences/APP_TABS");

        int authorizationIndex = indexOf(filters, AuthorizationFilter.class);
        int preferenceLimitIndex = indexOf(filters, UiPreferenceRequestSizeFilter.class);
        assertTrue(authorizationIndex >= 0);
        assertTrue(preferenceLimitIndex > authorizationIndex);
    }

    @Test
    void anonymousPreferencePutIsRejectedWithoutReadingRequestBody() throws Exception {
        AtomicBoolean bodyRead = new AtomicBoolean(false);

        int responseStatus = mockMvc.perform(put("/api/ui/preferences/APP_TABS")
                        .param("context", "workspace")
                        .contentType("application/json")
                        .content(VALID_BODY)
                        .with(trackBodyRead(bodyRead)))
                .andReturn()
                .getResponse()
                .getStatus();

        assertTrue(responseStatus == 401 || responseStatus == 403);
        assertFalse(bodyRead.get());
        verifyNoInteractions(preferenceService);
    }

    @Test
    @WithMockUser(username = "alice")
    void authenticatedChunkedOversizedPreferencePutReturns413BeforeService() throws Exception {
        AtomicBoolean bodyRead = new AtomicBoolean(false);
        String oversizedBody = "{\"schemaVersion\":1,\"revision\":0,\"payload\":{\"padding\":\""
                + "x".repeat(70_000)
                + "\"}}";

        mockMvc.perform(put("/api/ui/preferences/APP_TABS")
                        .param("context", "workspace")
                        .contentType("application/json")
                        .content(oversizedBody)
                        .with(asChunkedBody(bodyRead)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("UI preference request body is too large."));

        assertTrue(bodyRead.get());
        verifyNoInteractions(preferenceService);
    }

    private int indexOf(List<Filter> filters, Class<? extends Filter> type) {
        for (int index = 0; index < filters.size(); index++) {
            if (type.isInstance(filters.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private RequestPostProcessor trackBodyRead(AtomicBoolean bodyRead) {
        return request -> {
            try {
                return spyWithTrackedBody(request, bodyRead);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        };
    }

    private RequestPostProcessor asChunkedBody(AtomicBoolean bodyRead) {
        return request -> {
            try {
                MockHttpServletRequest tracked = spyWithTrackedBody(request, bodyRead);
                doReturn(-1).when(tracked).getContentLength();
                doReturn(-1L).when(tracked).getContentLengthLong();
                return tracked;
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        };
    }

    private MockHttpServletRequest spyWithTrackedBody(
            MockHttpServletRequest request,
            AtomicBoolean bodyRead
    ) throws IOException {
        MockHttpServletRequest tracked = spy(request);
        doAnswer(invocation -> {
            bodyRead.set(true);
            return invocation.callRealMethod();
        }).when(tracked).getInputStream();
        return tracked;
    }
}
