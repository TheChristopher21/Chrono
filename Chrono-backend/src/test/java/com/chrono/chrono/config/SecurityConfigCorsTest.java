package com.chrono.chrono.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigCorsTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://chrono-logisch.ch", "https://www.chrono-logisch.ch"})
    void permitsUnauthenticatedCompanyPreflightFromFrontend(String origin) throws Exception {
        var response = preflight(origin, "GET", "authorization,content-type,x-chrono-company");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
        assertThat(response.getHeader("Access-Control-Allow-Headers"))
                .contains("authorization", "content-type", "x-chrono-company");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @Test
    void permitsCompanyWritesWithIdempotencyKey() throws Exception {
        var response = preflight("https://chrono-logisch.ch", "PATCH",
                "authorization,content-type,x-chrono-company,idempotency-key");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("PATCH");
        assertThat(response.getHeader("Access-Control-Allow-Headers"))
                .contains("x-chrono-company", "idempotency-key");
    }

    @Test
    void rejectsCompanyPreflightFromUntrustedOrigin() throws Exception {
        var response = preflight("https://untrusted.example", "GET", "x-chrono-company");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    private MockHttpServletResponse preflight(String origin, String method, String headers) throws Exception {
        var filter = new CorsFilter(new SecurityConfig().corsConfigurationSource(
                "https://chrono-logisch.ch,https://www.chrono-logisch.ch"));
        var request = new MockHttpServletRequest("OPTIONS", "/api/auth/me");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", method);
        request.addHeader("Access-Control-Request-Headers", headers);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("Preflight must finish before authentication and company resolution");
        });
        return response;
    }

    @Test
    void parsesExplicitOriginsAndRemovesDuplicates() {
        assertThat(SecurityConfig.parseOrigins(
                "https://chrono-logisch.ch, https://www.chrono-logisch.ch,https://chrono-logisch.ch"))
                .containsExactly("https://chrono-logisch.ch", "https://www.chrono-logisch.ch");
    }

    @Test
    void rejectsWildcardOrigin() {
        assertThatThrownBy(() -> SecurityConfig.parseOrigins("https://*.chrono-logisch.ch"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CORS origins must be an explicit non-empty list");
    }
}
