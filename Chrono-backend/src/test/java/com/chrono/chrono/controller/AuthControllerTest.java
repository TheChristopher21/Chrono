package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.dto.LoginRequest;
import com.chrono.chrono.dto.RegisterRequest;
import com.chrono.chrono.services.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
public class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    public void testRegister() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        doNothing().when(authService).register(any(RegisterRequest.class));

        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"testuser\", \"password\":\"password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testLogin() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("testuser", "role", 1L, "token"));


        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"testuser\", \"password\":\"password\"}"))
                .andExpect(status().isOk());
    }
}
