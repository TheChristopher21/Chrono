package com.chrono.chrono.controller;

import com.chrono.chrono.dto.AuthResponse;
import com.chrono.chrono.dto.LoginRequest;
import com.chrono.chrono.dto.RegisterRequest;
import com.chrono.chrono.services.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    public void testRegister() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        when(authService.register(request)).thenReturn(new AuthResponse("token"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"testuser\", \"password\":\"password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testLogin() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        when(authService.login(request)).thenReturn(new AuthResponse("token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"testuser\", \"password\":\"password\"}"))
                .andExpect(status().isOk());
    }
}
