package com.chrono.chrono.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.WebUtils;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiPreferenceRequestSizeFilterTest {

    @Test
    void chunkedBodyWithoutContentLengthIsStillBounded() throws Exception {
        byte[] oversized = new byte[UiPreferenceRequestSizeFilter.MAX_REQUEST_BODY_BYTES + 1];
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletInputStream input = new DelegatingServletInputStream(new ByteArrayInputStream(oversized));
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/api/ui/preferences/APP_TABS");
        when(request.getContextPath()).thenReturn("");
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getContentLength()).thenReturn(-1);
        when(request.getInputStream()).thenReturn(input);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new UiPreferenceRequestSizeFilter().doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void asyncDispatchIsNotBufferedAgain() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(DispatcherType.ASYNC);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new UiPreferenceRequestSizeFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request, never()).getInputStream();
    }

    @Test
    void errorDispatchIsNotBufferedAgain() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(WebUtils.ERROR_REQUEST_URI_ATTRIBUTE))
                .thenReturn("/api/ui/preferences/APP_TABS");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new UiPreferenceRequestSizeFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request, never()).getInputStream();
    }
}
