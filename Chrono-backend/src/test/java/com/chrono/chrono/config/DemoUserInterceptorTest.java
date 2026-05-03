package com.chrono.chrono.config;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.DemoDataService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoUserInterceptorTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preHandleRejectsExpiredDemoUsers() throws Exception {
        DemoUserInterceptor interceptor = new DemoUserInterceptor();
        UserRepository userRepository = mock(UserRepository.class);
        DemoDataService demoDataService = mock(DemoDataService.class);
        ReflectionTestUtils.setField(interceptor, "userRepository", userRepository);
        ReflectionTestUtils.setField(interceptor, "demoDataService", demoDataService);

        User demoUser = new User();
        demoUser.setUsername("demo_expired");
        demoUser.setDemo(true);
        demoUser.setDemoExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByUsername("demo_expired")).thenReturn(Optional.of(demoUser));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("demo_expired", null, List.of())
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        org.junit.jupiter.api.Assertions.assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(demoDataService, never()).refreshDemoDataIfOutdated(demoUser);
    }

    @Test
    void preHandleBlocksUnlistedDemoWrites() throws Exception {
        DemoUserInterceptor interceptor = new DemoUserInterceptor();
        UserRepository userRepository = mock(UserRepository.class);
        DemoDataService demoDataService = mock(DemoDataService.class);
        ReflectionTestUtils.setField(interceptor, "userRepository", userRepository);
        ReflectionTestUtils.setField(interceptor, "demoDataService", demoDataService);

        User demoUser = new User();
        demoUser.setUsername("demo_active");
        demoUser.setDemo(true);
        demoUser.setDemoExpiresAt(LocalDateTime.now().plusHours(1));
        when(userRepository.findByUsername("demo_active")).thenReturn(Optional.of(demoUser));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("demo_active", null, List.of())
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/api/admin/users/demo_active_anna");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        org.junit.jupiter.api.Assertions.assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        verify(demoDataService).refreshDemoDataIfOutdated(demoUser);
    }

    @Test
    void preHandleAllowsListedDemoWrites() throws Exception {
        DemoUserInterceptor interceptor = new DemoUserInterceptor();
        UserRepository userRepository = mock(UserRepository.class);
        DemoDataService demoDataService = mock(DemoDataService.class);
        ReflectionTestUtils.setField(interceptor, "userRepository", userRepository);
        ReflectionTestUtils.setField(interceptor, "demoDataService", demoDataService);

        User demoUser = new User();
        demoUser.setUsername("demo_active");
        demoUser.setDemo(true);
        demoUser.setDemoExpiresAt(LocalDateTime.now().plusHours(1));
        when(userRepository.findByUsername("demo_active")).thenReturn(Optional.of(demoUser));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("demo_active", null, List.of())
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/vacation/create");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        verify(demoDataService).refreshDemoDataIfOutdated(demoUser);
    }
}
