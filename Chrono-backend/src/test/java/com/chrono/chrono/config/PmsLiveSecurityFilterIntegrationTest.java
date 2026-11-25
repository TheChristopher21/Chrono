package com.chrono.chrono.config;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "llm.warmup.enabled=false")
@ActiveProfiles("test")
class PmsLiveSecurityFilterIntegrationTest {
    @Autowired FilterChainProxy security;
    private boolean passes(String path, DispatcherType type, boolean previouslyAuthorized) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path); request.setDispatcherType(type);
        if (previouslyAuthorized) request.setAttribute(PmsAccessPolicy.LIVE_AUTHORIZED, true);
        var reached = new AtomicBoolean();
        security.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true));
        return reached.get();
    }
    @Test void anonymousInitialRequestsNeverReachTheStreamEvenWithAnInternalMarker() throws Exception {
        assertThat(passes("/api/pms/properties/9/live", DispatcherType.REQUEST, false)).isFalse();
        assertThat(passes("/api/pms/properties/9/live", DispatcherType.REQUEST, true)).isFalse();
    }
    @Test void onlyPreviouslyAuthorizedLiveStreamsCanCompleteOnAnAsyncDispatch() throws Exception {
        assertThat(passes("/api/pms/properties/9/live", DispatcherType.ASYNC, false)).isFalse();
        assertThat(passes("/api/pms/properties/9/live", DispatcherType.ASYNC, true)).isTrue();
        assertThat(passes("/api/pms/properties/9/payments", DispatcherType.ASYNC, true)).isFalse();
        assertThat(passes("/api/pms/properties/9/live", DispatcherType.ERROR, true)).isFalse();
    }
}
