package com.chrono.chrono.dto;

public class AuthResponse {
    private String username;
    private String role;
    private Long userId;
    private String token;

    public AuthResponse(String username, String role, Long userId, String token) {
        this.username = username;
        this.role = role;
        this.userId = userId;
        this.token = token;
    }

    // Getter und Setter
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
